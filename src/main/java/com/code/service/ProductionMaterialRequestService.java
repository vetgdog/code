package com.code.service;

import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.ProductionMaterialRequest;
import com.code.entity.ProductionMaterialRequestItem;
import com.code.entity.PurchaseRequest;
import com.code.entity.SalesOrder;
import com.code.entity.StockTransaction;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionMaterialRequestRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.UserRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 生产领料申请服务。
 *
 * <p>该服务把生产、仓库、采购三方围绕原材料的协同行为串成一条状态链：生产经理发起需求，仓库评估库存并发料，
 * 缺料时自动触发采购补料，最终随着生产完成与成品入库而闭环。由于它会同时修改申请单、库存、库存流水、采购申请
 * 和通知消息，因此天然属于事务型业务服务。</p>
 */
@Service
public class ProductionMaterialRequestService {

    /**
     * 初始状态：生产经理刚提交申请，仓库尚未确认是否能够立即发料。
     */
    public static final String STATUS_PENDING_WAREHOUSE_REVIEW = "待仓库备料";

    /**
     * 仓库审核后发现库存缺口，流程转给采购侧补料。
     */
    public static final String STATUS_WAITING_PROCUREMENT = "待采购补料";

    /**
     * 仓库已经成功执行原材料出库，生产侧可以基于已发料库存开始排产/开工。
     */
    public static final String STATUS_READY_FOR_PRODUCTION = "已备料待生产";

    /**
     * 生产已经结束，但成品尚未完成最终入库，因此领料流程还不能完全闭环。
     */
    public static final String STATUS_PRODUCTION_COMPLETED = "已完工待仓库入库";

    /**
     * 成品已回到成品仓，说明本次领料申请对应的业务链路已经闭环完成。
     */
    public static final String STATUS_WAREHOUSED = "已入成品库";

    private final ProductionMaterialRequestRepository requestRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 通过构造器注入所有依赖，保持 Service 本身无状态，便于 Spring 托管、单元测试替换依赖以及事务代理增强。
     */
    public ProductionMaterialRequestService(ProductionMaterialRequestRepository requestRepository,
                                            SalesOrderRepository salesOrderRepository,
                                            ProductRepository productRepository,
                                            InventoryItemRepository inventoryItemRepository,
                                            StockTransactionRepository stockTransactionRepository,
                                            PurchaseRequestRepository purchaseRequestRepository,
                                            UserRepository userRepository,
                                            NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.productRepository = productRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * 按角色和条件查询领料申请。
     *
     * <p>管理员、仓库经理、采购经理可查看全部；生产经理只能看到自己发起的申请，从而保证跨部门共享与最小可见范围之间的平衡。</p>
     */
    public List<ProductionMaterialRequest> listRequests(String role, String operatorEmail, Long orderId, String status) {
        String normalizedRole = normalizeRole(role);
        String normalizedEmail = safe(operatorEmail).toLowerCase(Locale.ROOT);
        String normalizedStatus = safe(status);
        User currentUser = resolveUserByEmail(normalizedEmail);

        // 这里先查全量再用 Stream 叠加过滤条件，优点是规则表达集中、易读；
        // 代价是当数据量增大时会把部分筛选压力放到内存层，后续可考虑下推到 Repository 查询。
        return requestRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(request -> orderId == null || matchesOrder(request, orderId))
                .filter(request -> normalizedStatus.isEmpty() || normalizedStatus.equalsIgnoreCase(safe(request.getStatus())))
                .filter(request -> canViewRequest(normalizedRole, currentUser, request))
                .collect(Collectors.toList());
    }

    /**
     * 创建领料申请。
     *
     * <p>只有“生产中”的销售订单才允许发起，且同一订单不允许并行存在多张未结束的领料申请，避免重复领料导致库存被多次扣减。</p>
     */
    @Transactional
    public ProductionMaterialRequest createRequest(Long orderId,
                                                   List<MaterialItemCommand> itemCommands,
                                                   String note,
                                                   String operatorEmail) {
        // 第一步先做入参与订单状态校验，尽早失败，避免后续进入库存/通知等重操作分支。
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择对应的生产订单");
        }
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + orderId));
        if (!OrderWorkflowService.STATUS_IN_PRODUCTION.equals(safe(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅生产中的订单可以发起原材料申请");
        }
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少添加一条原材料需求");
        }
        if (hasActiveRequest(orderId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该订单已有进行中的原材料申请，请勿重复提交");
        }

        User operator = requireCurrentUser(operatorEmail);

        // 这里初始化的是“流程主单”，后面的每一条原材料明细都会回挂到这个 request 上，
        // 形成 JPA 一对多关系，最终由 save(request) 统一持久化。
        ProductionMaterialRequest request = new ProductionMaterialRequest();
        request.setRequestNo(generateRequestNo());
        request.setSalesOrder(order);
        request.setFinishedProduct(resolveFinishedProduct(order));
        request.setStatus(STATUS_PENDING_WAREHOUSE_REVIEW);
        request.setRequestNote(blankToNull(note));
        request.setProcurementTriggered(false);
        request.setCreatedBy(operator.getId());
        request.setCreatedByName(resolveDisplayName(operator));
        request.setCreatedByEmail(operator.getEmail());
        request.setCreatedAt(LocalDateTime.now());

        List<ProductionMaterialRequestItem> items = new ArrayList<>();
        for (MaterialItemCommand command : itemCommands) {
            // 命令对象只承载“前端声明的需求”，真正可用于业务落库的 Product 必须重新查库确认，
            // 这样可以避免前端伪造名称、类型等关键字段。
            if (command == null || command.getMaterialProductId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料信息不能为空");
            }
            Product material = productRepository.findById(command.getMaterialProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料不存在: " + command.getMaterialProductId()));
            if (!"RAW_MATERIAL".equalsIgnoreCase(safe(material.getProductType()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持申请原材料: " + material.getName());
            }
            double requiredQuantity = safeNumber(command.getRequiredQuantity());
            if (requiredQuantity <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料需求数量必须大于0");
            }
            ProductionMaterialRequestItem item = new ProductionMaterialRequestItem();
            item.setRequest(request);
            item.setMaterialProduct(material);
            item.setRequiredQuantity(requiredQuantity);
            item.setIssuedQuantity(0.0);
            item.setAvailableQuantitySnapshot(0.0);
            item.setShortageQuantitySnapshot(0.0);
            items.add(item);
        }
        request.setItems(items);

        // 在事务内保存主单及其明细，若后续通知构建或业务异常抛错，Spring 会整体回滚，
        // 不会留下“只有申请单没有明细”之类的半成品数据。
        ProductionMaterialRequest saved = requestRepository.save(request);

        NotificationMessage warehouseMessage = buildRequestMessage(
                "PRODUCTION_MATERIAL_REQUEST_CREATED",
                saved,
                "生产领料申请 " + saved.getRequestNo() + " 已提交，请仓库审核原材料。",
                buildItemsSummary(saved)
        );
        notificationService.broadcast("/topic/orders/warehouse", warehouseMessage);
        notificationService.broadcast("/topic/production", warehouseMessage);
        return saved;
    }

    /**
     * 仓库审核领料申请。
     *
     * <p>这是流程分叉点：如果库存不足，则状态推进到“待采购补料”并自动生成采购申请；如果库存充足，则直接执行发料并将状态改成“已备料待生产”。</p>
     */
    @Transactional
    public ProductionMaterialRequest warehouseReview(Long requestId, String note, String operatorEmail) {
        ProductionMaterialRequest request = getRequest(requestId);

        // 仓库只能处理“待仓库备料”或“待采购补料”两个阶段，
        // 其他状态说明流程已经向后推进，再次审核会破坏状态机一致性。
        if (!List.of(STATUS_PENDING_WAREHOUSE_REVIEW, STATUS_WAITING_PROCUREMENT).contains(safe(request.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许仓库处理原材料申请: " + request.getStatus());
        }

        User warehouseManager = requireCurrentUser(operatorEmail);
        request.setWarehouseReviewedBy(warehouseManager.getId());
        request.setWarehouseReviewedByName(resolveDisplayName(warehouseManager));
        request.setWarehouseReviewedAt(LocalDateTime.now());
        request.setWarehouseNote(blankToNull(note));

        // 审核时不是只判断“够不够”，还要把当时看到的可用量/缺口写回明细，便于后续追责和审计。
        List<MaterialAvailability> shortages = evaluateAvailability(request);
        boolean hasShortage = shortages.stream().anyMatch(item -> item.shortageQuantity > 1e-6);
        if (hasShortage) {
            request.setStatus(STATUS_WAITING_PROCUREMENT);

            // procurementTriggered 是一个轻量级幂等保护位：
            // 同一张申请如果仓库多次进入缺料复审，不会重复生成多张采购申请。
            if (!Boolean.TRUE.equals(request.getProcurementTriggered())) {
                createPurchaseRequests(request, shortages, warehouseManager, note);
                request.setProcurementTriggered(true);
            }
            ProductionMaterialRequest saved = requestRepository.save(request);
            NotificationMessage shortageMessage = buildRequestMessage(
                    "PRODUCTION_MATERIAL_REQUEST_PENDING_PROCUREMENT",
                    saved,
                    "生产领料申请 " + saved.getRequestNo() + " 原材料不足，已通知采购管理员补料。",
                    summarizeShortages(shortages)
            );
            notificationService.broadcast("/topic/procurement/manager", shortageMessage);
            notificationService.broadcast("/topic/orders/warehouse", shortageMessage);
            return saved;
        }

        // 走到这里说明库存充足，可以立即扣减原材料库存并登记出库流水。
        issueMaterials(request, warehouseManager, note);
        request.setStatus(STATUS_READY_FOR_PRODUCTION);
        request.setMaterialsIssuedAt(LocalDateTime.now());
        ProductionMaterialRequest saved = requestRepository.save(request);
        NotificationMessage readyMessage = buildRequestMessage(
                "PRODUCTION_MATERIAL_REQUEST_READY",
                saved,
                "生产领料申请 " + saved.getRequestNo() + " 原材料已出库，可以开始生产。",
                buildItemsSummary(saved)
        );
        notificationService.broadcast("/topic/production", readyMessage);
        String managerTopic = resolveProductionManagerTopic(saved.getCreatedByEmail());
        notificationService.broadcast(managerTopic, readyMessage);
        return saved;
    }

    /**
     * 生产完工前的前置校验：必须已经存在一张备料完成的领料申请。
     */
    public ProductionMaterialRequest requireReadyRequestForOrder(Long orderId) {
        // findFirst 取得最新排序结果中的第一条“已备料待生产”申请，
        // 既避免生产基于旧申请开工，也让订单只与最近一次有效备料记录绑定。
        return requestRepository.findBySalesOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(request -> STATUS_READY_FOR_PRODUCTION.equals(safe(request.getStatus())))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先提交原材料申请，并等待仓库备料完成后再进行生产"));
    }

    /**
     * 标记生产完工。
     *
     * <p>这里不处理库存扣减，因为原材料已经在仓库审核通过时出库；本方法仅推进领料申请状态，为后续成品入库闭环做准备。</p>
     */
    @Transactional
    public void markProductionCompleted(Long orderId) {
        ProductionMaterialRequest request = requireReadyRequestForOrder(orderId);

        // 这里只改变状态，不重复处理原材料库存。
        // 原材料在仓库备料通过时就已经扣减，若此处再扣一次会造成双扣。
        request.setStatus(STATUS_PRODUCTION_COMPLETED);
        request.setProductionCompletedAt(LocalDateTime.now());
        requestRepository.save(request);
    }

    /**
     * 标记成品已入库，结束领料申请生命周期。
     */
    @Transactional
    public void markWarehoused(Long orderId) {
        // 使用 ifPresent 而不是强制抛错，表示“成品入库联动更新领料状态”属于补充闭环动作：
        // 若未找到待入库申请，则主流程不因该步骤硬失败。
        requestRepository.findBySalesOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(request -> STATUS_PRODUCTION_COMPLETED.equals(safe(request.getStatus())))
                .findFirst()
                .ifPresent(request -> {
                    request.setStatus(STATUS_WAREHOUSED);
                    request.setWarehousedAt(LocalDateTime.now());
                    requestRepository.save(request);
                });
    }

    private boolean canViewRequest(String normalizedRole, User currentUser, ProductionMaterialRequest request) {
        // 这里是最小权限可见范围控制：跨部门管理岗可看全量，生产经理仅限本人发起数据。
        if ("ROLE_ADMIN".equals(normalizedRole) || "ROLE_WAREHOUSE_MANAGER".equals(normalizedRole) || "ROLE_PROCUREMENT_MANAGER".equals(normalizedRole)) {
            return true;
        }
        if ("ROLE_PRODUCTION_MANAGER".equals(normalizedRole)) {
            return currentUser != null && currentUser.getId() != null && currentUser.getId().equals(request.getCreatedBy());
        }
        return false;
    }

    private boolean matchesOrder(ProductionMaterialRequest request, Long orderId) {
        // 空值防御写法可以避免在链式对象关系缺失时抛 NPE，尤其是历史脏数据场景。
        return request != null
                && request.getSalesOrder() != null
                && request.getSalesOrder().getId() != null
                && request.getSalesOrder().getId().equals(orderId);
    }

    /**
     * 判断订单是否已经存在“尚未闭环”的领料申请。
     *
     * <p>这里把待仓库、待采购、待生产、待入库都视为活跃态，核心目的不是限制创建动作本身，
     * 而是防止同一订单在流程未结束前重复领料、重复触发采购。</p>
     */
    private boolean hasActiveRequest(Long orderId) {
        return requestRepository.findBySalesOrderIdOrderByCreatedAtDesc(orderId).stream()
                .anyMatch(request -> List.of(
                        STATUS_PENDING_WAREHOUSE_REVIEW,
                        STATUS_WAITING_PROCUREMENT,
                        STATUS_READY_FOR_PRODUCTION,
                        STATUS_PRODUCTION_COMPLETED
                ).contains(safe(request.getStatus())));
    }

    /**
     * 从销售订单中解析本次生产对应的成品。
     *
     * <p>当前实现直接取第一条订单明细的产品，隐含前提是一次生产领料申请主要服务于当前订单的主生产成品。
     * 如果后续订单支持真正的多成品并行生产，这里可能需要升级为显式传入 productId。</p>
     */
    private Product resolveFinishedProduct(SalesOrder order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return null;
        }
        return order.getItems().get(0).getProduct();
    }

    /**
     * 计算每条原材料明细的可用量与缺口，并把快照写回申请明细。
     *
     * <p>这样即使后续库存继续变化，申请单上仍能保留仓库审核当时看到的库存快照，方便事后追溯。</p>
     */
    private List<MaterialAvailability> evaluateAvailability(ProductionMaterialRequest request) {
        List<MaterialAvailability> results = new ArrayList<>();
        List<ProductionMaterialRequestItem> items = request.getItems() == null ? List.of() : request.getItems();
        for (ProductionMaterialRequestItem item : items) {
            double available = totalAvailableByProduct(item.getMaterialProduct() == null ? null : item.getMaterialProduct().getId());
            double required = safeNumber(item.getRequiredQuantity());
            double shortage = Math.max(0.0, required - available);
            item.setAvailableQuantitySnapshot(available);
            item.setShortageQuantitySnapshot(shortage);
            results.add(new MaterialAvailability(item, available, shortage));
        }
        return results;
    }

    /**
     * 对缺料项自动生成采购申请。
     *
     * <p>注意这里只创建采购申请而非采购单，说明仓库负责提出补料事实，采购经理仍保有后续选供应商、合单和执行采购的权限。</p>
     */
    private void createPurchaseRequests(ProductionMaterialRequest request,
                                        List<MaterialAvailability> shortages,
                                        User warehouseManager,
                                        String note) {
        for (MaterialAvailability availability : shortages) {
            // 只为真实缺口创建采购申请，避免“库存充足但也生成 OPEN 采购单”的脏数据。
            if (availability.shortageQuantity <= 1e-6 || availability.item.getMaterialProduct() == null) {
                continue;
            }
            PurchaseRequest purchaseRequest = new PurchaseRequest();
            purchaseRequest.setRequestNo(generatePurchaseRequestNo());
            purchaseRequest.setRequestedBy(warehouseManager.getId());
            purchaseRequest.setRequestedByName(resolveDisplayName(warehouseManager));
            purchaseRequest.setProduct(availability.item.getMaterialProduct());
            purchaseRequest.setRequestedQuantity(round2(availability.shortageQuantity));
            purchaseRequest.setRequestDate(LocalDateTime.now());
            purchaseRequest.setStatus("OPEN");
            purchaseRequest.setNotes(buildPurchaseRequestNote(request, availability, note));
            purchaseRequestRepository.save(purchaseRequest);
        }
    }

    /**
     * 生成采购申请备注，将“缺料来源”编码进文本，方便采购经理在列表页快速理解上下文。
     */
    private String buildPurchaseRequestNote(ProductionMaterialRequest request, MaterialAvailability availability, String note) {
        StringBuilder builder = new StringBuilder();
        builder.append("来源生产领料申请 ")
                .append(request.getRequestNo())
                .append(" / 订单 ")
                .append(request.getSalesOrder() == null ? "-" : safe(request.getSalesOrder().getOrderNo()))
                .append("，原材料 ")
                .append(availability.item.getMaterialProduct() == null ? "-" : safe(availability.item.getMaterialProduct().getName()))
                .append(" 缺口 ")
                .append(String.format(Locale.ROOT, "%.2f", round2(availability.shortageQuantity)));
        if (!safe(note).isEmpty()) {
            builder.append("；仓库说明：").append(note.trim());
        }
        return builder.toString();
    }

    /**
     * 实际执行原材料出库。
     *
     * <p>当前策略是按库存记录 id 顺序依次扣减，可视为一种简化的固定顺序发料规则。它没有严格实现 FIFO/FEFO，但通过记录 lot 字段，
     * 为后续升级批次优先策略保留了接口与审计基础。</p>
     */
    private void issueMaterials(ProductionMaterialRequest request, User warehouseManager, String note) {
        List<ProductionMaterialRequestItem> items = request.getItems() == null ? List.of() : request.getItems();
        for (ProductionMaterialRequestItem item : items) {
            Product material = item.getMaterialProduct();
            if (material == null || material.getId() == null) {
                continue;
            }

            // remaining 表示该物料还需要从多少库存记录中继续扣减，
            // 这种“逐条扣减直到为 0”的写法适合映射多仓位/多批次库存的拆分消耗过程。
            double remaining = safeNumber(item.getRequiredQuantity());
            List<InventoryItem> inventoryItems = new ArrayList<>(inventoryItemRepository.findByProductId(material.getId()));

            // 这里按 id 升序排序，本质上是一种稳定但简化的发料顺序。
            // 它保证同样的数据每次扣减路径一致，便于测试和审计，但不等同于严格业务 FIFO/FEFO。
            inventoryItems.sort(Comparator.comparing(inv -> inv.getId() == null ? Long.MAX_VALUE : inv.getId()));
            for (InventoryItem inventoryItem : inventoryItems) {
                if (remaining <= 1e-6) {
                    break;
                }
                double available = Math.max(0.0, safeNumber(inventoryItem.getQuantity()) - safeNumber(inventoryItem.getReservedQuantity()));
                if (available <= 1e-6) {
                    continue;
                }
                double consume = Math.min(available, remaining);

                // quantity 代表实际库存余额，reservedQuantity 代表预留但尚未真正消耗的数量。
                // 这里扣减的是可用余额，不动预留字段，避免影响其他流程的锁库语义。
                inventoryItem.setQuantity(safeNumber(inventoryItem.getQuantity()) - consume);
                inventoryItem.setUpdatedAt(LocalDateTime.now());
                inventoryItemRepository.save(inventoryItem);
                createStockTransaction(material, inventoryItem.getWarehouse(), consume, warehouseManager, request, note, inventoryItem.getLot());
                remaining -= consume;
            }
            if (remaining > 1e-6) {
                // 即便仓库审核阶段判断过库存充足，这里仍再次兜底校验，
                // 用于防御并发下库存被其他流程抢占导致的瞬时不足问题。
                throw new ResponseStatusException(HttpStatus.CONFLICT, "原材料库存不足，无法完成出库，请稍后重试");
            }
            item.setIssuedQuantity(safeNumber(item.getRequiredQuantity()));
            item.setShortageQuantitySnapshot(0.0);
        }
    }

    /**
     * 为领料出库生成库存流水，使每次原材料消耗都能追溯到具体的领料申请。
     */
    private void createStockTransaction(Product material,
                                        Warehouse warehouse,
                                        double quantity,
                                        User operator,
                                        ProductionMaterialRequest request,
                                        String note,
                                        String lot) {
        StockTransaction tx = new StockTransaction();

        // 库存余额表只回答“现在还剩多少”，库存流水则回答“为什么会变、谁改的、关联哪个业务单据”，
        // 两者组合后系统才具备审计追溯能力。
        tx.setTransactionNo("ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        tx.setProduct(material);
        tx.setWarehouse(warehouse);
        tx.setChangeQuantity(round2(quantity));
        tx.setTransactionType("OUT");
        tx.setLot(blankToNull(lot));
        tx.setRelatedType("PRODUCTION_MATERIAL_REQUEST");
        tx.setRelatedId(request.getId());
        tx.setRemark(buildStockRemark(request, note));
        tx.setCreatedBy(operator == null ? null : operator.getId());
        tx.setCreatedByName(operator == null ? null : resolveDisplayName(operator));
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(tx);
    }

    /**
     * 构建库存流水备注，让仓库、审计或问题排查人员能从文本直接回溯到源领料申请和销售订单。
     */
    private String buildStockRemark(ProductionMaterialRequest request, String note) {
        StringBuilder builder = new StringBuilder();
        builder.append("生产领料申请 ")
                .append(request.getRequestNo())
                .append(" / 订单 ")
                .append(request.getSalesOrder() == null ? "-" : safe(request.getSalesOrder().getOrderNo()));
        if (!safe(note).isEmpty()) {
            builder.append("；").append(note.trim());
        }
        return builder.toString();
    }

    /**
     * 汇总指定原材料的可用库存。
     *
     * <p>这里用 Stream + mapToDouble + sum 聚合多个库存记录，表达上比传统 for 循环更紧凑；
     * 同时显式扣除 reservedQuantity，避免把已经被其他流程占用的库存误判为可发料库存。</p>
     */
    private double totalAvailableByProduct(Long productId) {
        if (productId == null) {
            return 0.0;
        }
        return inventoryItemRepository.findByProductId(productId).stream()
                .mapToDouble(item -> Math.max(0.0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity())))
                .sum();
    }

    /**
     * 读取领料申请，不存在则抛 404，供控制流程统一复用。
     */
    private ProductionMaterialRequest getRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产领料申请不存在: " + requestId));
    }

    /**
     * 解析当前操作人账号。
     *
     * <p>之所以这里直接抛 403，而不是返回 null 让上层判空，是为了让“认证主体缺失”尽早失败，
     * 防止后续产生 createdBy 为空、流水操作人缺失等审计不完整的数据。</p>
     */
    private User requireCurrentUser(String email) {
        return userRepository.findByEmailIgnoreCase(safe(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作"));
    }

    /**
     * 宽松按邮箱解析用户。适用于列表查询这类“拿不到用户也可以继续返回结果”的场景。
     */
    private User resolveUserByEmail(String email) {
        if (safe(email).isEmpty()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "";
        }
        return safe(user.getName()).isEmpty() ? safe(user.getEmail()) : user.getName().trim();
    }

    /**
     * 生成领料申请编号。
     *
     * <p>当前用 UUID 截断实现，优点是简单、分布式下冲突概率低；缺点是不可读性一般，后续可替换为带日期/业务语义的编码规则。</p>
     */
    private String generateRequestNo() {
        return "PMR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /**
     * 生成补料采购申请编号，和常规采购单做前缀区分，便于后续统计“由缺料触发”的采购来源。
     */
    private String generatePurchaseRequestNo() {
        return "PR-MAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /**
     * 统一构建领料流程相关的通知消息。
     */
    private NotificationMessage buildRequestMessage(String type,
                                                    ProductionMaterialRequest request,
                                                    String title,
                                                    String meta) {
        // 统一消息模型可以让 WebSocket 前端订阅方按 type + entityType 做分发，
        // 避免每种流程都发完全不同结构的消息，降低前端通知处理复杂度。
        return new NotificationMessage(type, "ProductionMaterialRequest", request.getId(),
                new MaterialRequestNoticePayload(request, title, meta), LocalDateTime.now());
    }

    /**
     * 把申请明细压缩成适合通知栏展示的一行摘要，避免消息面板里直接塞完整明细表。
     */
    private String buildItemsSummary(ProductionMaterialRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return "暂无原材料明细";
        }
        return request.getItems().stream()
                .map(item -> (item.getMaterialProduct() == null ? "原材料" : item.getMaterialProduct().getName()) + " x " + String.format(Locale.ROOT, "%.2f", safeNumber(item.getRequiredQuantity())))
                .collect(Collectors.joining("；"));
    }

    /**
     * 汇总缺料明细，用于通知采购侧快速识别本次需要补哪些原材料。
     */
    private String summarizeShortages(List<MaterialAvailability> shortages) {
        return shortages.stream()
                .filter(item -> item.shortageQuantity > 1e-6 && item.item.getMaterialProduct() != null)
                .map(item -> item.item.getMaterialProduct().getName() + " 缺口 " + String.format(Locale.ROOT, "%.2f", round2(item.shortageQuantity)))
                .collect(Collectors.joining("；"));
    }

    /**
     * 将邮箱转换成可用于 topic 路径的安全片段。
     *
     * <p>这里通过正则把非字母数字替换为横线，避免邮箱中的 @、. 等字符直接出现在 WebSocket topic 中，
     * 减少路径兼容性问题，并为“按人推送”提供稳定的订阅地址。</p>
     */
    private String resolveProductionManagerTopic(String email) {
        String normalized = safe(email).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "/topic/production" : "/topic/production/manager/" + normalized;
    }

    private String normalizeRole(String role) {
        if (safe(role).isEmpty()) {
            return "";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    /**
     * 字符串空值收敛工具，避免业务代码中反复出现 null 判断。
     */
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 数值空值收敛工具，统一把 null 当作 0 处理，减少数量计算时的 NPE 风险。
     */
    private double safeNumber(Double value) {
        return value == null ? 0.0 : value;
    }

    /**
     * 统一金额/数量保留两位小数，避免库存流水与采购申请在展示层出现过长尾数。
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 将空白字符串规范成 null，便于数据库层区分“没有填写”而不是“填写了空串”。
     */
    private String blankToNull(String value) {
        return safe(value).isEmpty() ? null : value.trim();
    }

    /**
     * 仓库审核阶段的可用量快照。
     */
    private static class MaterialAvailability {
        private final ProductionMaterialRequestItem item;

        /**
         * 审核时可立即用于出库的库存量快照。
         */
        private final double availableQuantity;

        /**
         * 审核时计算出的缺口数量，用于决定是否触发采购补料。
         */
        private final double shortageQuantity;

        private MaterialAvailability(ProductionMaterialRequestItem item, double availableQuantity, double shortageQuantity) {
            this.item = item;
            this.availableQuantity = availableQuantity;
            this.shortageQuantity = shortageQuantity;
        }
    }

    /**
     * 创建领料申请时的原材料命令对象。
     */
    public static class MaterialItemCommand {
        private final Long materialProductId;
        private final Double requiredQuantity;

        /**
         * 前端提交领料申请时使用的轻量命令对象。
         *
         * <p>它只保留最小必要字段：物料主键 + 需求数量，避免把完整 Product 对象暴露成接口入参，
         * 既降低耦合，也减少客户端篡改服务端关键属性的机会。</p>
         */
        public MaterialItemCommand(Long materialProductId, Double requiredQuantity) {
            this.materialProductId = materialProductId;
            this.requiredQuantity = requiredQuantity;
        }

        public Long getMaterialProductId() {
            return materialProductId;
        }

        public Double getRequiredQuantity() {
            return requiredQuantity;
        }
    }

    /**
     * 领料流程推送给前端的消息载荷。
     */
    public static class MaterialRequestNoticePayload {
        private final ProductionMaterialRequest request;
        private final String notificationTitle;
        private final String notificationMeta;

        /**
         * 前端通知载荷。
         *
         * <p>同时携带完整申请对象与摘要文本，原因是不同页面的信息密度不同：
         * 列表页可能只关心标题/摘要，详情页则可能直接复用 request 做局部刷新。</p>
         */
        public MaterialRequestNoticePayload(ProductionMaterialRequest request, String notificationTitle, String notificationMeta) {
            this.request = request;
            this.notificationTitle = notificationTitle;
            this.notificationMeta = notificationMeta;
        }

        public ProductionMaterialRequest getRequest() {
            return request;
        }

        public String getNotificationTitle() {
            return notificationTitle;
        }

        public String getNotificationMeta() {
            return notificationMeta;
        }
    }
}

