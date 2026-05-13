package com.code.service;

import com.code.dto.ProcurementExportRowDto;
import com.code.dto.SupplierDashboardDto;
import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseOrderItem;
import com.code.entity.PurchaseRequest;
import com.code.entity.StockTransaction;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.UserRepository;
import com.code.repository.WarehouseRepository;
import com.code.support.WarehouseDefaults;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * 采购工作流服务。
 *
 * <p>该服务把采购业务从“采购经理建单”到“供应商接单/拒绝/发货”再到“仓库收货入库”串成一条完整状态链。
 * 它不仅维护采购单状态，还会联动采购申请、库存余额、库存流水以及 WebSocket 通知，因此是典型的事务型工作流服务。</p>
 *
 * <p>从设计上看，这里采用显式状态常量 + 单方法推进一步状态的方式，优点是每个节点职责清晰、易于做权限和测试；
 * 缺点是当状态继续增多时，可能需要演进成更正式的状态机封装。</p>
 */
@Service
public class ProcurementWorkflowService {

    private static final DateTimeFormatter PURCHASE_ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 采购经理已建单，等待供应商确认是否接单。
     */
    public static final String STATUS_WAITING_SUPPLIER = "待供应商确认";

    /**
     * 供应商接受采购任务，接下来可以进入发货环节。
     */
    public static final String STATUS_SUPPLIER_ACCEPTED = "供应商已接单";

    /**
     * 供应商拒单，采购侧需要重新处理供应关系或重新建单。
     */
    public static final String STATUS_SUPPLIER_REJECTED = "供应商已拒绝";

    /**
     * 供应商已发货，但仓库尚未确认实物入库。
     */
    public static final String STATUS_SUPPLIER_SHIPPED = "供应商已发货";

    /**
     * 采购侧已通知仓库到货，等待仓库执行确认收货。
     */
    public static final String STATUS_WAITING_WAREHOUSE_RECEIPT = "待仓库收货";

    /**
     * 仓库确认收货并完成库存入账，采购单流程闭环结束。
     */
    public static final String STATUS_WAREHOUSED = "已入库";

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * 创建采购单。
     *
     * <p>该方法会同时完成：供应商校验、原材料合法性校验、明细标准化、金额汇总、状态初始化以及源采购申请转单标记。
     * 因为这些动作必须一起成功或一起失败，所以放在一个事务里处理。</p>
     */
    @Transactional
    public PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder, String operator) {
        if (purchaseOrder == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单数据不能为空");
        }
        User operatorUser = userRepository.findByEmailIgnoreCase(safe(operator)).orElse(null);
        if (purchaseOrder.getSupplier() == null || purchaseOrder.getSupplier().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择供应商");
        }
        User supplier = userRepository.findById(purchaseOrder.getSupplier().getId())
                .filter(this::isSupplierUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商不存在: " + purchaseOrder.getSupplier().getId()));
        if (purchaseOrder.getItems() == null || purchaseOrder.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少添加一条采购明细");
        }

        purchaseOrder.setPoNo(generatePurchaseOrderNo());
        double totalAmount = 0.0;
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (PurchaseOrderItem rawItem : purchaseOrder.getItems()) {

            // 前端传回来的采购明细只当作“意图输入”，真正用于落单的 Product 必须重新查库确认，
            // 这样可防止前端伪造产品类型、供应商信息或价格字段。
            if (rawItem == null || rawItem.getProduct() == null || rawItem.getProduct().getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购明细缺少原材料信息");
            }
            Product product = productRepository.findById(rawItem.getProduct().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料不存在: " + rawItem.getProduct().getId()));
            if (!"RAW_MATERIAL".equalsIgnoreCase(product.getProductType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持采购原材料: " + product.getName());
            }
            if (!hasMaintainedSupplier(product)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料未维护供应商信息: " + product.getName());
            }
            if (!isSupplierRelatedMaterial(product, supplier)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料 " + product.getName() + " 与所选供应商不匹配");
            }
            double quantity = rawItem.getQuantity() == null ? 0.0 : rawItem.getQuantity();
            if (quantity <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购数量必须大于0");
            }
            double unitPrice = rawItem.getUnitPrice() == null ? safeNumber(product.getUnitPrice()) : rawItem.getUnitPrice();
            if (unitPrice < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单价不能小于0");
            }

            // 这里重建 item 而不是直接复用前端对象，是一种典型的“服务端标准化组装”写法，
            // 便于统一补齐 purchaseOrder、lineTotal 等由后端控制的字段。
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(purchaseOrder);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setLineTotal(quantity * unitPrice);
            totalAmount += item.getLineTotal();
            items.add(item);
        }

        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setItems(items);
        purchaseOrder.setTotalAmount(totalAmount);
        purchaseOrder.setStatus(STATUS_WAITING_SUPPLIER);
        purchaseOrder.setCreatedBy(operatorUser == null ? null : operatorUser.getId());
        purchaseOrder.setCreatedByName(resolveDisplayName(operatorUser, operator));
        purchaseOrder.setOrderDate(purchaseOrder.getOrderDate() == null ? LocalDateTime.now() : purchaseOrder.getOrderDate());
        purchaseOrder.setCreatedAt(purchaseOrder.getCreatedAt() == null ? LocalDateTime.now() : purchaseOrder.getCreatedAt());
        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);

        // 若采购单来源于采购申请，这里会把上游申请状态同步改成 CONVERTED，
        // 避免同一申请被重复转单。
        markSourceRequestsConverted(saved);

        notificationService.broadcast(
                resolveSupplierTopic(supplier),
                buildOrderMessage(
                        "PROCUREMENT_ORDER_CREATED",
                        saved,
                        operator,
                        "采购单 " + saved.getPoNo() + " 已发送，请处理。",
                        buildLineSummary(saved)
                )
        );
        return saved;
    }

    /**
     * 供应商对采购单做接单/拒单决策。
     */
    @Transactional
    public PurchaseOrder supplierDecision(Long purchaseOrderId, String decision, String operator, String note) {
        PurchaseOrder order = getOrder(purchaseOrderId);
        if (!STATUS_WAITING_SUPPLIER.equals(safe(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许供应商处理: " + order.getStatus());
        }
        String normalizedDecision = safe(decision).toUpperCase(Locale.ROOT);
        if (!"ACCEPT".equals(normalizedDecision) && !"REJECT".equals(normalizedDecision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision 仅支持 ACCEPT 或 REJECT");
        }

        order.setSupplierNote(blankToNull(note));

        // 供应商决策只允许从 WAITING_SUPPLIER 进入 ACCEPT/REJECT，
        // 这是采购状态机最基本的单向流转规则。
        if ("ACCEPT".equals(normalizedDecision)) {
            order.setStatus(STATUS_SUPPLIER_ACCEPTED);
            notifyProcurementManager(order, "PROCUREMENT_ORDER_ACCEPTED", operator, "采购单 " + order.getPoNo() + " 供应商已接单。", note);
        } else {
            order.setStatus(STATUS_SUPPLIER_REJECTED);
            notifyProcurementManager(order, "PROCUREMENT_ORDER_REJECTED", operator, "采购单 " + order.getPoNo() + " 供应商已拒绝，请查看。", note);
        }
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        notificationService.broadcast("/topic/procurement", buildOrderMessage("PROCUREMENT_ORDER_UPDATED", saved, operator));
        return saved;
    }

    /**
     * 供应商发货。
     *
     * <p>这里只推进采购单状态，不直接影响库存；库存变更必须等仓库确认实物到货后再入账。</p>
     */
    @Transactional
    public PurchaseOrder supplierShip(Long purchaseOrderId, String operator, String note) {
        PurchaseOrder order = getOrder(purchaseOrderId);
        if (!STATUS_SUPPLIER_ACCEPTED.equals(safe(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许发货: " + order.getStatus());
        }
        order.setStatus(STATUS_SUPPLIER_SHIPPED);
        order.setSupplierNote(blankToNull(note));
        order.setShippedAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        notifyProcurementManager(saved, "PROCUREMENT_ORDER_SHIPPED", operator, "采购单 " + saved.getPoNo() + " 供应商已发货，请查看。", note);
        notificationService.broadcast("/topic/procurement", buildOrderMessage("PROCUREMENT_ORDER_UPDATED", saved, operator));
        return saved;
    }

    /**
     * 采购经理通知仓库准备收货。
     */
    @Transactional
    public PurchaseOrder notifyWarehouseForReceipt(Long purchaseOrderId, String operator, String note) {
        PurchaseOrder order = getOrder(purchaseOrderId);
        if (!STATUS_SUPPLIER_SHIPPED.equals(safe(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许通知仓库收货: " + order.getStatus());
        }
        order.setStatus(STATUS_WAITING_WAREHOUSE_RECEIPT);
        order.setProcurementNote(blankToNull(note));
        order.setNotifiedWarehouseAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        notificationService.broadcast(
                "/topic/procurement/warehouse",
                buildOrderMessage(
                        "PROCUREMENT_WAREHOUSE_CONFIRM_REQUIRED",
                        saved,
                        operator,
                        "采购单 " + saved.getPoNo() + " 已到货，请仓库确认入库。",
                        buildLineSummary(saved)
                )
        );
        notificationService.broadcast("/topic/procurement", buildOrderMessage("PROCUREMENT_ORDER_UPDATED", saved, operator));
        return saved;
    }

    /**
     * 仓库确认收货并自动入库。
     *
     * <p>这是采购流程中最关键的有库存副作用节点：每条采购明细都会落到对应仓库库存，并同步生成 IN 类型库存流水。</p>
     */
    @Transactional
    public PurchaseOrder warehouseReceive(Long purchaseOrderId, String operator, String note) {
        PurchaseOrder order = getOrder(purchaseOrderId);
        if (!STATUS_WAITING_WAREHOUSE_RECEIPT.equals(safe(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许仓库收货: " + order.getStatus());
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单没有明细，无法入库");
        }

        User operatorUser = userRepository.findByEmailIgnoreCase(safe(operator)).orElse(null);
        for (PurchaseOrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }

            // 入哪个仓库不是前端传入决定，而是由系统根据已有库存分布或默认仓规则自动解析，
            // 避免采购收货时把同一种原材料分散入错仓。
            Warehouse warehouse = resolveWarehouseForProduct(product.getId());
            InventoryItem inventoryItem = findOrCreateInventoryItem(product, warehouse);
            inventoryItem.setQuantity(safeNumber(inventoryItem.getQuantity()) + safeNumber(item.getQuantity()));
            inventoryItem.setUpdatedAt(LocalDateTime.now());
            inventoryItemRepository.save(inventoryItem);
            createStockTransaction(product, warehouse, safeNumber(item.getQuantity()), "IN", "PURCHASE_ORDER", order.getId(), operatorUser, blankToNull(note));
        }

        order.setStatus(STATUS_WAREHOUSED);
        order.setWarehouseNote(blankToNull(note));
        order.setReceivedAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        notifyProcurementManager(saved, "PROCUREMENT_ORDER_WAREHOUSED", operator, "采购单 " + saved.getPoNo() + " 已确认收货并自动入库。", note);
        notificationService.broadcast(resolveSupplierTopic(saved.getSupplier()), buildOrderMessage("PROCUREMENT_ORDER_WAREHOUSED", saved, operator, "采购单 " + saved.getPoNo() + " 已完成收货入库。", note));
        notificationService.broadcast("/topic/procurement", buildOrderMessage("PROCUREMENT_ORDER_UPDATED", saved, operator));
        return saved;
    }

    /**
     * 按角色列采购单：供应商只能看自己的单，其他角色看全量。
     */
    public List<PurchaseOrder> listOrders(String role, String email) {
        String normalizedRole = safe(role).toUpperCase(Locale.ROOT);
        if (normalizedRole.contains("SUPPLIER")) {
            User supplier = resolveSupplierAccount(email);
            return purchaseOrderRepository.findBySupplierIdOrderByOrderDateDesc(supplier.getId());
        }
        return purchaseOrderRepository.findAllByOrderByOrderDateDesc();
    }

    /**
     * 构建供应商首页摘要。
     *
     * <p>该方法聚合待确认、待发货、已发货等统计指标，并补充推荐原材料和待办采购单，方便供应商登录后快速把握当前工作量。</p>
     */
    public SupplierDashboardDto buildSupplierDashboard(String email) {
        User supplier = resolveSupplierAccount(email);
        List<PurchaseOrder> orders = purchaseOrderRepository.findBySupplierIdOrderByOrderDateDesc(supplier.getId());

        int pendingConfirmCount = (int) orders.stream().filter(order -> STATUS_WAITING_SUPPLIER.equals(safe(order.getStatus()))).count();
        int acceptedPendingShipCount = (int) orders.stream().filter(order -> STATUS_SUPPLIER_ACCEPTED.equals(safe(order.getStatus()))).count();
        int shippedCount = (int) orders.stream().filter(order -> STATUS_SUPPLIER_SHIPPED.equals(safe(order.getStatus()))).count();
        int totalOpenOrders = (int) orders.stream()
                .filter(order -> !STATUS_WAREHOUSED.equals(safe(order.getStatus())))
                .filter(order -> !STATUS_SUPPLIER_REJECTED.equals(safe(order.getStatus())))
                .count();

        List<SupplierDashboardDto.RecommendedMaterial> recommendedMaterials = resolveRecommendedMaterials(supplier, orders);
        List<SupplierDashboardDto.TodoOrder> todoOrders = orders.stream()
                .filter(order -> STATUS_WAITING_SUPPLIER.equals(safe(order.getStatus()))
                        || STATUS_SUPPLIER_ACCEPTED.equals(safe(order.getStatus()))
                        || STATUS_SUPPLIER_SHIPPED.equals(safe(order.getStatus())))
                .limit(6)
                .map(order -> new SupplierDashboardDto.TodoOrder(
                        order.getId(),
                        order.getPoNo(),
                        order.getStatus(),
                        buildLineSummary(order),
                        order.getTotalAmount()
                ))
                .collect(Collectors.toList());

        return new SupplierDashboardDto(
                supplierDisplayName(supplier),
                pendingConfirmCount,
                acceptedPendingShipCount,
                shippedCount,
                totalOpenOrders,
                recommendedMaterials,
                todoOrders
        );
    }

    /**
     * 构建采购导出行。
     *
     * <p>导出时按“采购单 + 明细”展开成扁平行，方便 Excel 报表消费；若没有明细，则仍输出一行主单信息保证记录不丢失。</p>
     */
    public List<ProcurementExportRowDto> buildExportRows(List<PurchaseOrder> orders) {
        List<ProcurementExportRowDto> rows = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            if (order.getItems() == null || order.getItems().isEmpty()) {
                rows.add(new ProcurementExportRowDto(
                        order.getPoNo(),
                        order.getStatus(),
                        order.getSupplier() == null ? "" : safe(order.getSupplier().getCode()),
                        order.getSupplier() == null ? "" : safe(order.getSupplier().getName()),
                        "",
                        "",
                        0.0,
                        0.0,
                        0.0,
                        order.getTotalAmount(),
                        formatDateTime(order.getOrderDate()),
                        formatDateTime(order.getShippedAt()),
                        formatDateTime(order.getReceivedAt()),
                        safe(order.getSupplierNote()),
                        safe(order.getProcurementNote()),
                        safe(order.getWarehouseNote())
                ));
                continue;
            }
            for (PurchaseOrderItem item : order.getItems()) {
                rows.add(new ProcurementExportRowDto(
                        order.getPoNo(),
                        order.getStatus(),
                        order.getSupplier() == null ? "" : safe(order.getSupplier().getCode()),
                        order.getSupplier() == null ? "" : safe(order.getSupplier().getName()),
                        item.getProduct() == null ? "" : safe(item.getProduct().getSku()),
                        item.getProduct() == null ? "" : safe(item.getProduct().getName()),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal(),
                        order.getTotalAmount(),
                        formatDateTime(order.getOrderDate()),
                        formatDateTime(order.getShippedAt()),
                        formatDateTime(order.getReceivedAt()),
                        safe(order.getSupplierNote()),
                        safe(order.getProcurementNote()),
                        safe(order.getWarehouseNote())
                ));
            }
        }
        return rows;
    }

    /**
     * 查询待仓库收货的采购单，供仓库页面聚焦处理待办。
     */
    public List<PurchaseOrder> listPendingWarehouseReceipts() {
        return purchaseOrderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(order -> STATUS_WAITING_WAREHOUSE_RECEIPT.equals(safe(order.getStatus())))
                .collect(Collectors.toList());
    }

    /**
     * 按主键读取采购单，不存在则抛 404。
     */
    private PurchaseOrder getOrder(Long purchaseOrderId) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在: " + purchaseOrderId));
    }

    /**
     * 将源采购申请标记为已转单。
     *
     * <p>这一步保证采购申请与采购单之间形成可追溯关系，并防止同一 OPEN 申请被重复转换为多张采购单。</p>
     */
    private void markSourceRequestsConverted(PurchaseOrder order) {
        if (order == null || order.getSourceRequestIds() == null || order.getSourceRequestIds().isEmpty()) {
            return;
        }
        List<Long> requestIds = order.getSourceRequestIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (requestIds.isEmpty()) {
            return;
        }
        List<PurchaseRequest> requests = purchaseRequestRepository.findAllById(requestIds);
        if (requests.size() != requestIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在无效的采购申请来源，请刷新后重试");
        }
        for (PurchaseRequest request : requests) {
            if (!"OPEN".equalsIgnoreCase(safe(request.getStatus()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购申请已被处理: " + request.getRequestNo());
            }
            request.setStatus("CONVERTED");
            request.setSupplier(order.getSupplier());
            request.setNotes(appendConversionNote(request.getNotes(), order.getPoNo()));
        }
        purchaseRequestRepository.saveAll(requests);
    }

    /**
     * 在采购申请备注中附加“已生成采购单”信息，方便回溯上游来源。
     */
    private String appendConversionNote(String existingNote, String poNo) {
        String conversionNote = "已生成采购单：" + safe(poNo);
        if (safe(existingNote).isEmpty()) {
            return conversionNote;
        }
        if (safe(existingNote).contains(safe(poNo))) {
            return existingNote;
        }
        return existingNote.trim() + "；" + conversionNote;
    }

    /**
     * 生成采购单号。
     *
     * <p>编码由时间戳 + 随机后缀组成，并做最多 10 次存在性重试，平衡可读性与冲突概率。</p>
     */
    private String generatePurchaseOrderNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
            String candidate = "PO" + LocalDateTime.now().format(PURCHASE_ORDER_NO_FORMATTER) + suffix;
            if (!purchaseOrderRepository.existsByPoNo(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "采购单号生成失败，请稍后重试");
    }

    /**
     * 解析当前供应商账号，并强制其必须具备供应商角色。
     */
    public User resolveSupplierAccount(String email) {
        String normalizedEmail = safe(email).toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定供应商信息");
        }
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(this::isSupplierUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是供应商账号"));
    }

    /**
     * 通知采购经理关注采购单状态变化。
     */
    private void notifyProcurementManager(PurchaseOrder order, String messageType, String operator, String title, String note) {
        notificationService.broadcast(
                "/topic/procurement/manager",
                buildOrderMessage(messageType, order, operator, title, note)
        );
    }

    /**
     * 构建采购流程通用消息体。
     */
    private NotificationMessage buildOrderMessage(String type, PurchaseOrder order, String operator) {
        ProcurementEvent event = new ProcurementEvent(order, operator, "", "");
        return new NotificationMessage(type, "PurchaseOrder", order.getId(), event, LocalDateTime.now());
    }

    /**
     * 构建带标题与摘要信息的采购消息体，方便前端通知栏直接渲染。
     */
    private NotificationMessage buildOrderMessage(String type, PurchaseOrder order, String operator, String notificationTitle, String notificationMeta) {
        ProcurementEvent event = new ProcurementEvent(order, operator, notificationTitle, notificationMeta);
        return new NotificationMessage(type, "PurchaseOrder", order.getId(), event, LocalDateTime.now());
    }

    /**
     * 为供应商构造专属 topic，支持按账号精准推送采购单通知。
     */
    private String resolveSupplierTopic(User supplier) {
        if (supplier == null || supplier.getEmail() == null) {
            return "/topic/procurement/supplier";
        }
        String normalized = supplier.getEmail().trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "/topic/procurement/supplier" : "/topic/procurement/supplier/" + normalized;
    }

    /**
     * 将采购单明细压缩成一段可读摘要，适合消息通知、看板卡片和导出备注使用。
     */
    private String buildLineSummary(PurchaseOrder order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "暂无采购明细";
        }
        return order.getItems().stream()
                .map(item -> (item.getProduct() == null ? "原材料" : item.getProduct().getName()) + " x " + safeNumber(item.getQuantity()))
                .collect(Collectors.joining("；"));
    }

    /**
     * 解析供应商首页推荐原材料。
     *
     * <p>优先展示该供应商绑定的原材料；如果主数据尚未维护绑定关系，则退化为从历史采购单里反推，提升首页可用性。</p>
     */
    private List<SupplierDashboardDto.RecommendedMaterial> resolveRecommendedMaterials(User supplier, List<PurchaseOrder> orders) {
        Map<Long, Product> recommended = new LinkedHashMap<>();

        productRepository.findAll().stream()
                .filter(product -> "RAW_MATERIAL".equalsIgnoreCase(product.getProductType()))
                .filter(product -> isSupplierRelatedMaterial(product, supplier))
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(product -> recommended.put(product.getId(), product));

        if (recommended.isEmpty()) {
            orders.stream()
                    .flatMap(order -> order.getItems() == null ? java.util.stream.Stream.empty() : order.getItems().stream())
                    .map(PurchaseOrderItem::getProduct)
                    .filter(product -> product != null && product.getId() != null)
                    .forEach(product -> recommended.put(product.getId(), product));
        }

        return recommended.values().stream()
                .limit(8)
                .map(product -> new SupplierDashboardDto.RecommendedMaterial(
                        product.getId(),
                        product.getSku(),
                        product.getName(),
                        product.getMaterialCategory(),
                        product.getSpecification(),
                        product.getPreferredSupplier(),
                        product.getUnitPrice(),
                        product.getSafetyStock()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 判断某原材料是否属于该供应商。
     *
     * <p>当前通过 preferredSupplier 文本模糊匹配供应商名称/编码/邮箱，优点是兼容历史数据，
     * 缺点是规范性较弱，未来更稳妥的做法是建立显式外键或中间表关联。</p>
     */
    public boolean isSupplierRelatedMaterial(Product product, User supplier) {
        if (product == null || supplier == null) {
            return false;
        }
        String supplierName = normalize(supplierDisplayName(supplier));
        String supplierCode = normalize(supplier.getCode());
        String supplierEmail = normalize(supplier.getEmail());
        String preferredSupplier = normalize(product.getPreferredSupplier());
        return (!supplierName.isEmpty() && preferredSupplier.contains(supplierName))
                || (!supplierCode.isEmpty() && preferredSupplier.contains(supplierCode))
                || (!supplierEmail.isEmpty() && preferredSupplier.contains(supplierEmail));
    }

    /**
     * 查询已绑定原材料的供应商列表，供采购页面下拉选择或统计展示使用。
     */
    public List<User> listSuppliersBoundToRawMaterials() {
        List<Product> rawMaterials = productRepository.findAll().stream()
                .filter(product -> "RAW_MATERIAL".equalsIgnoreCase(product.getProductType()))
                .filter(this::hasMaintainedSupplier)
                .collect(Collectors.toList());

        return userRepository.findDistinctByRoles_NameOrderByIdAsc("ROLE_SUPPLIER").stream()
                .filter(supplier -> rawMaterials.stream().anyMatch(product -> isSupplierRelatedMaterial(product, supplier)))
                .sorted(Comparator.comparing(User::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 判断用户是否具备供应商角色。
     */
    private boolean isSupplierUser(User user) {
        return user != null
                && user.getRoles() != null
                && user.getRoles().stream()
                .map(role -> role == null ? "" : safe(role.getName()).toUpperCase(Locale.ROOT))
                .anyMatch("ROLE_SUPPLIER"::equals);
    }

    /**
     * 供应商展示名优先级：全名 -> 用户名 -> 邮箱。
     */
    private String supplierDisplayName(User supplier) {
        if (supplier == null) {
            return "";
        }
        String fullName = safe(supplier.getFullName());
        if (!fullName.isEmpty()) {
            return fullName;
        }
        String username = safe(supplier.getUsername());
        if (!username.isEmpty()) {
            return username;
        }
        return safe(supplier.getEmail());
    }

    /**
     * 判断原材料是否维护了首选供应商信息。
     */
    private boolean hasMaintainedSupplier(Product product) {
        return product != null && !safe(product.getPreferredSupplier()).isEmpty();
    }

    /**
     * 查找或创建产品-仓库维度的库存记录。
     */
    private InventoryItem findOrCreateInventoryItem(Product product, Warehouse warehouse) {
        return inventoryItemRepository.findByProductId(product.getId()).stream()
                .filter(item -> item.getWarehouse() != null && item.getWarehouse().getId() != null)
                .filter(item -> item.getWarehouse().getId().equals(warehouse.getId()))
                .findFirst()
                .orElseGet(() -> {
                    InventoryItem item = new InventoryItem();
                    item.setProduct(product);
                    item.setWarehouse(warehouse);
                    item.setQuantity(0.0);
                    item.setReservedQuantity(0.0);
                    return item;
                });
    }

    /**
     * 为采购入库解析目标仓库。
     *
     * <p>优先复用该物料已有库存所在仓库；若尚无库存，则根据原材料/成品默认仓规则选仓，
     * 最后再兜底取最小 id 仓库，确保采购收货流程不会因缺少显式仓库选择而中断。</p>
     */
    private Warehouse resolveWarehouseForProduct(Long productId) {
        Warehouse existing = inventoryItemRepository.findByProductId(productId).stream()
                .map(InventoryItem::getWarehouse)
                .filter(warehouse -> warehouse != null && warehouse.getId() != null)
                .min(Comparator.comparing(Warehouse::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        Product product = productRepository.findById(productId).orElse(null);
        String defaultWarehouseCode = WarehouseDefaults.defaultWarehouseCodeFor(product);
        return warehouseRepository.findByCodeIgnoreCase(defaultWarehouseCode)
                .or(() -> warehouseRepository.findAll().stream()
                        .sorted(Comparator.comparing(Warehouse::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "未配置仓库，无法完成采购入库"));
    }

    /**
     * 创建库存流水，使采购收货带来的库存变化具备完整审计链。
     */
    private void createStockTransaction(Product product,
                                        Warehouse warehouse,
                                        double quantity,
                                        String type,
                                        String relatedType,
                                        Long relatedId,
                                        User operatorUser,
                                        String remark) {
        StockTransaction tx = new StockTransaction();
        tx.setTransactionNo("ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        tx.setProduct(product);
        tx.setWarehouse(warehouse);
        tx.setChangeQuantity(quantity);
        tx.setTransactionType(type);
        tx.setRelatedType(relatedType);
        tx.setRelatedId(relatedId);
        tx.setCreatedBy(operatorUser == null ? null : operatorUser.getId());
        tx.setCreatedByName(resolveDisplayName(operatorUser, null));
        tx.setRemark(remark);
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(tx);
    }

    /**
     * 统一解析操作人显示名：优先真实姓名，缺失时回退备用值。
     */
    private String resolveDisplayName(User user, String fallback) {
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return blankToNull(fallback);
    }

    private double safeNumber(Double value) {
        return value == null ? 0.0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String blankToNull(String value) {
        return safe(value).isEmpty() ? null : value.trim();
    }

    /**
     * 推送给前端的采购事件载荷。
     *
     * <p>同时携带采购单、操作人和通知摘要，便于前端不同页面按需渲染列表、详情和消息提醒。</p>
     */
    public static class ProcurementEvent {
        private final PurchaseOrder order;
        private final String operator;
        private final String notificationTitle;
        private final String notificationMeta;

        public ProcurementEvent(PurchaseOrder order, String operator, String notificationTitle, String notificationMeta) {
            this.order = order;
            this.operator = operator;
            this.notificationTitle = notificationTitle;
            this.notificationMeta = notificationMeta;
        }

        public PurchaseOrder getOrder() {
            return order;
        }

        public String getOperator() {
            return operator;
        }

        public String getNotificationTitle() {
            return notificationTitle;
        }

        public String getNotificationMeta() {
            return notificationMeta;
        }
    }
}

