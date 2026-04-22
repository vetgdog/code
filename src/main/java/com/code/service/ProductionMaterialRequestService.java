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

@Service
public class ProductionMaterialRequestService {

    public static final String STATUS_PENDING_WAREHOUSE_REVIEW = "待仓库备料";
    public static final String STATUS_WAITING_PROCUREMENT = "待采购补料";
    public static final String STATUS_READY_FOR_PRODUCTION = "已备料待生产";
    public static final String STATUS_PRODUCTION_COMPLETED = "已完工待仓库入库";
    public static final String STATUS_WAREHOUSED = "已入成品库";

    private final ProductionMaterialRequestRepository requestRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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

    public List<ProductionMaterialRequest> listRequests(String role, String operatorEmail, Long orderId, String status) {
        String normalizedRole = normalizeRole(role);
        String normalizedEmail = safe(operatorEmail).toLowerCase(Locale.ROOT);
        String normalizedStatus = safe(status);
        User currentUser = resolveUserByEmail(normalizedEmail);

        return requestRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(request -> orderId == null || matchesOrder(request, orderId))
                .filter(request -> normalizedStatus.isEmpty() || normalizedStatus.equalsIgnoreCase(safe(request.getStatus())))
                .filter(request -> canViewRequest(normalizedRole, currentUser, request))
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductionMaterialRequest createRequest(Long orderId,
                                                   List<MaterialItemCommand> itemCommands,
                                                   String note,
                                                   String operatorEmail) {
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

    @Transactional
    public ProductionMaterialRequest warehouseReview(Long requestId, String note, String operatorEmail) {
        ProductionMaterialRequest request = getRequest(requestId);
        if (!List.of(STATUS_PENDING_WAREHOUSE_REVIEW, STATUS_WAITING_PROCUREMENT).contains(safe(request.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许仓库处理原材料申请: " + request.getStatus());
        }

        User warehouseManager = requireCurrentUser(operatorEmail);
        request.setWarehouseReviewedBy(warehouseManager.getId());
        request.setWarehouseReviewedByName(resolveDisplayName(warehouseManager));
        request.setWarehouseReviewedAt(LocalDateTime.now());
        request.setWarehouseNote(blankToNull(note));

        List<MaterialAvailability> shortages = evaluateAvailability(request);
        boolean hasShortage = shortages.stream().anyMatch(item -> item.shortageQuantity > 1e-6);
        if (hasShortage) {
            request.setStatus(STATUS_WAITING_PROCUREMENT);
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

    public ProductionMaterialRequest requireReadyRequestForOrder(Long orderId) {
        return requestRepository.findBySalesOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(request -> STATUS_READY_FOR_PRODUCTION.equals(safe(request.getStatus())))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先提交原材料申请，并等待仓库备料完成后再进行生产"));
    }

    @Transactional
    public void markProductionCompleted(Long orderId) {
        ProductionMaterialRequest request = requireReadyRequestForOrder(orderId);
        request.setStatus(STATUS_PRODUCTION_COMPLETED);
        request.setProductionCompletedAt(LocalDateTime.now());
        requestRepository.save(request);
    }

    @Transactional
    public void markWarehoused(Long orderId) {
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
        if ("ROLE_ADMIN".equals(normalizedRole) || "ROLE_WAREHOUSE_MANAGER".equals(normalizedRole) || "ROLE_PROCUREMENT_MANAGER".equals(normalizedRole)) {
            return true;
        }
        if ("ROLE_PRODUCTION_MANAGER".equals(normalizedRole)) {
            return currentUser != null && currentUser.getId() != null && currentUser.getId().equals(request.getCreatedBy());
        }
        return false;
    }

    private boolean matchesOrder(ProductionMaterialRequest request, Long orderId) {
        return request != null
                && request.getSalesOrder() != null
                && request.getSalesOrder().getId() != null
                && request.getSalesOrder().getId().equals(orderId);
    }

    private boolean hasActiveRequest(Long orderId) {
        return requestRepository.findBySalesOrderIdOrderByCreatedAtDesc(orderId).stream()
                .anyMatch(request -> List.of(
                        STATUS_PENDING_WAREHOUSE_REVIEW,
                        STATUS_WAITING_PROCUREMENT,
                        STATUS_READY_FOR_PRODUCTION,
                        STATUS_PRODUCTION_COMPLETED
                ).contains(safe(request.getStatus())));
    }

    private Product resolveFinishedProduct(SalesOrder order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return null;
        }
        return order.getItems().get(0).getProduct();
    }

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

    private void createPurchaseRequests(ProductionMaterialRequest request,
                                        List<MaterialAvailability> shortages,
                                        User warehouseManager,
                                        String note) {
        for (MaterialAvailability availability : shortages) {
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

    private void issueMaterials(ProductionMaterialRequest request, User warehouseManager, String note) {
        List<ProductionMaterialRequestItem> items = request.getItems() == null ? List.of() : request.getItems();
        for (ProductionMaterialRequestItem item : items) {
            Product material = item.getMaterialProduct();
            if (material == null || material.getId() == null) {
                continue;
            }
            double remaining = safeNumber(item.getRequiredQuantity());
            List<InventoryItem> inventoryItems = new ArrayList<>(inventoryItemRepository.findByProductId(material.getId()));
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
                inventoryItem.setQuantity(safeNumber(inventoryItem.getQuantity()) - consume);
                inventoryItem.setUpdatedAt(LocalDateTime.now());
                inventoryItemRepository.save(inventoryItem);
                createStockTransaction(material, inventoryItem.getWarehouse(), consume, warehouseManager, request, note, inventoryItem.getLot());
                remaining -= consume;
            }
            if (remaining > 1e-6) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "原材料库存不足，无法完成出库，请稍后重试");
            }
            item.setIssuedQuantity(safeNumber(item.getRequiredQuantity()));
            item.setShortageQuantitySnapshot(0.0);
        }
    }

    private void createStockTransaction(Product material,
                                        Warehouse warehouse,
                                        double quantity,
                                        User operator,
                                        ProductionMaterialRequest request,
                                        String note,
                                        String lot) {
        StockTransaction tx = new StockTransaction();
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

    private double totalAvailableByProduct(Long productId) {
        if (productId == null) {
            return 0.0;
        }
        return inventoryItemRepository.findByProductId(productId).stream()
                .mapToDouble(item -> Math.max(0.0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity())))
                .sum();
    }

    private ProductionMaterialRequest getRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产领料申请不存在: " + requestId));
    }

    private User requireCurrentUser(String email) {
        return userRepository.findByEmailIgnoreCase(safe(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作"));
    }

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

    private String generateRequestNo() {
        return "PMR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String generatePurchaseRequestNo() {
        return "PR-MAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private NotificationMessage buildRequestMessage(String type,
                                                    ProductionMaterialRequest request,
                                                    String title,
                                                    String meta) {
        return new NotificationMessage(type, "ProductionMaterialRequest", request.getId(),
                new MaterialRequestNoticePayload(request, title, meta), LocalDateTime.now());
    }

    private String buildItemsSummary(ProductionMaterialRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return "暂无原材料明细";
        }
        return request.getItems().stream()
                .map(item -> (item.getMaterialProduct() == null ? "原材料" : item.getMaterialProduct().getName()) + " x " + String.format(Locale.ROOT, "%.2f", safeNumber(item.getRequiredQuantity())))
                .collect(Collectors.joining("；"));
    }

    private String summarizeShortages(List<MaterialAvailability> shortages) {
        return shortages.stream()
                .filter(item -> item.shortageQuantity > 1e-6 && item.item.getMaterialProduct() != null)
                .map(item -> item.item.getMaterialProduct().getName() + " 缺口 " + String.format(Locale.ROOT, "%.2f", round2(item.shortageQuantity)))
                .collect(Collectors.joining("；"));
    }

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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private double safeNumber(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String blankToNull(String value) {
        return safe(value).isEmpty() ? null : value.trim();
    }

    private static class MaterialAvailability {
        private final ProductionMaterialRequestItem item;
        private final double availableQuantity;
        private final double shortageQuantity;

        private MaterialAvailability(ProductionMaterialRequestItem item, double availableQuantity, double shortageQuantity) {
            this.item = item;
            this.availableQuantity = availableQuantity;
            this.shortageQuantity = shortageQuantity;
        }
    }

    public static class MaterialItemCommand {
        private final Long materialProductId;
        private final Double requiredQuantity;

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

    public static class MaterialRequestNoticePayload {
        private final ProductionMaterialRequest request;
        private final String notificationTitle;
        private final String notificationMeta;

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

