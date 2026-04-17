package com.code.controller;

import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.ProductionPlan;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseOrderItem;
import com.code.entity.PurchaseRequest;
import com.code.entity.Role;
import com.code.entity.User;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.repository.UserRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/inventory/alerts")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
public class InventoryAlertController {

    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public InventoryAlertController(ProductRepository productRepository,
                                    InventoryItemRepository inventoryItemRepository,
                                    ProductionPlanRepository productionPlanRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    PurchaseRequestRepository purchaseRequestRepository,
                                    UserRepository userRepository,
                                    NotificationService notificationService) {
        this.productRepository = productRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.productionPlanRepository = productionPlanRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public InventoryAlertSummaryView listAlerts() {
        List<InventoryAlertItemView> finishedGoods = productRepository.findByProductTypeOrderByCreatedAtDesc("FINISHED_GOOD").stream()
                .map(product -> buildAlert(product, true))
                .filter(item -> item.getShortageQuantity() > 0)
                .sorted(Comparator.comparing(InventoryAlertItemView::getShortageQuantity, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        List<InventoryAlertItemView> rawMaterials = productRepository.findByProductTypeOrderByCreatedAtDesc("RAW_MATERIAL").stream()
                .map(product -> buildAlert(product, false))
                .filter(item -> item.getShortageQuantity() > 0)
                .sorted(Comparator.comparing(InventoryAlertItemView::getShortageQuantity, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        return new InventoryAlertSummaryView(finishedGoods, rawMaterials, LocalDateTime.now());
    }

    @PostMapping("/finished-goods/{productId}/production-plan")
    public ProductionPlan createProductionPlan(@PathVariable Long productId,
                                               @RequestBody(required = false) AlertActionRequest request,
                                               Authentication authentication) {
        Product product = requireProduct(productId, "FINISHED_GOOD");
        User operator = requireCurrentUser(authentication);
        InventoryAlertItemView alert = buildAlert(product, true);
        double quantity = request == null || request.getQuantity() == null || request.getQuantity() <= 0
                ? alert.getRecommendedActionQuantity()
                : request.getQuantity();
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前产品无需补充生产计划");
        }

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanNo("PLAN-ALERT-" + product.getId() + "-" + System.currentTimeMillis());
        plan.setProduct(product);
        plan.setPlannedQuantity(quantity);
        plan.setStatus("PLANNED");
        plan.setStartDate(LocalDateTime.now());
        plan.setEndDate(LocalDateTime.now().plusDays(7));
        plan.setCreatedBy(operator.getId());
        plan.setCreatedByName(resolveDisplayName(operator));
        ProductionPlan saved = productionPlanRepository.save(plan);

        notificationService.broadcast(
                "/topic/orders/production",
                new NotificationMessage(
                        "INVENTORY_ALERT_PRODUCTION_PLAN_CREATED",
                        "ProductionPlan",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        notificationService.broadcast(
                "/topic/production",
                new NotificationMessage(
                        "INVENTORY_ALERT_PRODUCTION_PLAN_CREATED",
                        "ProductionPlan",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        return saved;
    }

    @PostMapping("/raw-materials/{productId}/purchase-request")
    public PurchaseRequest createPurchaseRequest(@PathVariable Long productId,
                                                 @RequestBody(required = false) AlertActionRequest request,
                                                 Authentication authentication) {
        Product product = requireProduct(productId, "RAW_MATERIAL");
        User operator = requireCurrentUser(authentication);
        InventoryAlertItemView alert = buildAlert(product, false);
        double quantity = request == null || request.getQuantity() == null || request.getQuantity() <= 0
                ? alert.getRecommendedActionQuantity()
                : request.getQuantity();
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前原材料无需补充采购申请");
        }

        PurchaseRequest requestEntity = new PurchaseRequest();
        requestEntity.setRequestNo(generatePurchaseRequestNo());
        requestEntity.setRequestedBy(operator.getId());
        requestEntity.setRequestedByName(resolveDisplayName(operator));
        requestEntity.setProduct(product);
        requestEntity.setRequestedQuantity(quantity);
        requestEntity.setRequestDate(LocalDateTime.now());
        requestEntity.setStatus("OPEN");
        requestEntity.setNotes(request == null || isBlank(request.getNote())
                ? "库存预警触发，建议采购数量：" + String.format(Locale.ROOT, "%.2f", quantity)
                : request.getNote().trim());
        PurchaseRequest saved = purchaseRequestRepository.save(requestEntity);

        notificationService.broadcast(
                "/topic/procurement/manager",
                new NotificationMessage(
                        "INVENTORY_ALERT_PURCHASE_REQUEST_CREATED",
                        "PurchaseRequest",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        notificationService.broadcast(
                "/topic/procurement",
                new NotificationMessage(
                        "INVENTORY_ALERT_PURCHASE_REQUEST_CREATED",
                        "PurchaseRequest",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        return saved;
    }

    private InventoryAlertItemView buildAlert(Product product, boolean finishedGood) {
        double totalQuantity = inventoryItemRepository.findByProductId(product.getId()).stream()
                .mapToDouble(item -> safe(item.getQuantity()))
                .sum();
        double reservedQuantity = inventoryItemRepository.findByProductId(product.getId()).stream()
                .mapToDouble(item -> safe(item.getReservedQuantity()))
                .sum();
        double availableQuantity = Math.max(0.0, totalQuantity - reservedQuantity);
        double safetyStock = safe(product.getSafetyStock());
        double pipelineQuantity = finishedGood ? inProductionQuantity(product.getId()) : inTransitQuantity(product.getId());
        double shortageQuantity = Math.max(0.0, round2(safetyStock - availableQuantity));
        double recommendedQuantity = Math.max(0.0, round2(safetyStock - availableQuantity - pipelineQuantity));
        String severity = availableQuantity <= 0.000001
                ? "CRITICAL"
                : (availableQuantity < safetyStock * 0.5 ? "HIGH" : "MEDIUM");
        String warehouseSummary = inventoryItemRepository.findByProductId(product.getId()).stream()
                .sorted(Comparator.comparing(item -> item.getWarehouse() == null ? "" : safe(item.getWarehouse().getName())))
                .map(this::formatWarehouseLine)
                .collect(Collectors.joining("；"));
        String suggestedAction = finishedGood ? "建议补充生产计划" : "建议通知采购管理员创建采购申请";
        return new InventoryAlertItemView(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getProductType(),
                totalQuantity,
                availableQuantity,
                reservedQuantity,
                safetyStock,
                pipelineQuantity,
                shortageQuantity,
                recommendedQuantity,
                severity,
                suggestedAction,
                warehouseSummary,
                safe(product.getPreferredSupplier())
        );
    }

    private double inProductionQuantity(Long productId) {
        return productionPlanRepository.findAll().stream()
                .filter(plan -> plan.getProduct() != null && productId.equals(plan.getProduct().getId()))
                .filter(plan -> {
                    String status = safe(plan.getStatus()).toUpperCase(Locale.ROOT);
                    return !"WAREHOUSED".equals(status) && !"DONE".equals(status);
                })
                .mapToDouble(plan -> safe(plan.getPlannedQuantity()))
                .sum();
    }

    private double inTransitQuantity(Long productId) {
        return purchaseOrderRepository.findAll().stream()
                .filter(order -> List.of(
                        com.code.service.ProcurementWorkflowService.STATUS_WAITING_SUPPLIER,
                        com.code.service.ProcurementWorkflowService.STATUS_SUPPLIER_ACCEPTED,
                        com.code.service.ProcurementWorkflowService.STATUS_SUPPLIER_SHIPPED,
                        com.code.service.ProcurementWorkflowService.STATUS_WAITING_WAREHOUSE_RECEIPT
                ).contains(safe(order.getStatus())))
                .flatMap(order -> order.getItems() == null ? java.util.stream.Stream.<PurchaseOrderItem>empty() : order.getItems().stream())
                .filter(item -> item.getProduct() != null && productId.equals(item.getProduct().getId()))
                .mapToDouble(item -> safe(item.getQuantity()))
                .sum();
    }

    private Product requireProduct(Long productId, String productType) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "产品不存在: " + productId));
        if (!productType.equalsIgnoreCase(safe(product.getProductType()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品类型不匹配");
        }
        return product;
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作"));
    }

    private String resolveDisplayName(User user) {
        return user == null ? "" : (isBlank(user.getName()) ? safe(user.getEmail()) : user.getName());
    }

    private String formatWarehouseLine(InventoryItem item) {
        String warehouseName = item.getWarehouse() == null ? "未分仓" : safe(item.getWarehouse().getName());
        return warehouseName + " 可用 " + String.format(Locale.ROOT, "%.2f", Math.max(0.0, safe(item.getQuantity()) - safe(item.getReservedQuantity())));
    }

    private String generatePurchaseRequestNo() {
        return "PR-ALERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static class InventoryAlertSummaryView {
        private final List<InventoryAlertItemView> finishedGoods;
        private final List<InventoryAlertItemView> rawMaterials;
        private final LocalDateTime generatedAt;

        public InventoryAlertSummaryView(List<InventoryAlertItemView> finishedGoods,
                                         List<InventoryAlertItemView> rawMaterials,
                                         LocalDateTime generatedAt) {
            this.finishedGoods = finishedGoods;
            this.rawMaterials = rawMaterials;
            this.generatedAt = generatedAt;
        }

        public List<InventoryAlertItemView> getFinishedGoods() { return finishedGoods; }
        public List<InventoryAlertItemView> getRawMaterials() { return rawMaterials; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
    }

    public static class InventoryAlertItemView {
        private final Long productId;
        private final String sku;
        private final String name;
        private final String productType;
        private final Double totalQuantity;
        private final Double availableQuantity;
        private final Double reservedQuantity;
        private final Double safetyStock;
        private final Double pipelineQuantity;
        private final Double shortageQuantity;
        private final Double recommendedActionQuantity;
        private final String severity;
        private final String suggestedAction;
        private final String warehouseSummary;
        private final String preferredSupplier;

        public InventoryAlertItemView(Long productId,
                                      String sku,
                                      String name,
                                      String productType,
                                      Double totalQuantity,
                                      Double availableQuantity,
                                      Double reservedQuantity,
                                      Double safetyStock,
                                      Double pipelineQuantity,
                                      Double shortageQuantity,
                                      Double recommendedActionQuantity,
                                      String severity,
                                      String suggestedAction,
                                      String warehouseSummary,
                                      String preferredSupplier) {
            this.productId = productId;
            this.sku = sku;
            this.name = name;
            this.productType = productType;
            this.totalQuantity = totalQuantity;
            this.availableQuantity = availableQuantity;
            this.reservedQuantity = reservedQuantity;
            this.safetyStock = safetyStock;
            this.pipelineQuantity = pipelineQuantity;
            this.shortageQuantity = shortageQuantity;
            this.recommendedActionQuantity = recommendedActionQuantity;
            this.severity = severity;
            this.suggestedAction = suggestedAction;
            this.warehouseSummary = warehouseSummary;
            this.preferredSupplier = preferredSupplier;
        }

        public Long getProductId() { return productId; }
        public String getSku() { return sku; }
        public String getName() { return name; }
        public String getProductType() { return productType; }
        public Double getTotalQuantity() { return totalQuantity; }
        public Double getAvailableQuantity() { return availableQuantity; }
        public Double getReservedQuantity() { return reservedQuantity; }
        public Double getSafetyStock() { return safetyStock; }
        public Double getPipelineQuantity() { return pipelineQuantity; }
        public Double getShortageQuantity() { return shortageQuantity; }
        public Double getRecommendedActionQuantity() { return recommendedActionQuantity; }
        public String getSeverity() { return severity; }
        public String getSuggestedAction() { return suggestedAction; }
        public String getWarehouseSummary() { return warehouseSummary; }
        public String getPreferredSupplier() { return preferredSupplier; }
    }

    public static class AlertActionRequest {
        private Double quantity;
        private String note;

        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}

