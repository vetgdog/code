package com.code.controller;

import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.ProductionPlan;
import com.code.entity.PurchaseOrderItem;
import com.code.entity.PurchaseRequest;
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

/**
 * 库存预警控制器。
 *
 * <p>该控制器面向仓库经理与管理员提供“成品缺货预警 + 原材料缺料预警”能力，核心目标不是单纯展示库存，
 * 而是把库存、预留量、安全库存、在途量/在产量综合成一个可执行的业务判断结果。前端看到的每一条预警，
 * 实际上都已经是一个被聚合后的“行动建议”：成品走生产补计划，原材料走采购补申请。</p>
 *
 * <p>这里的设计明显偏向教学/演示型 ERP：聚合计算直接在控制器内完成，便于串起产品、库存、生产、采购、
 * WebSocket 通知四条链路。优点是阅读路径短；代价是单次查询会多次访问仓储层，数据量大时需要进一步下沉到
 * Service + 专用聚合 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/inventory/alerts")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
public class InventoryAlertController {

    /**
     * 产品主数据来源，决定哪些成品/原材料需要进入预警计算范围。
     */
    private final ProductRepository productRepository;

    /**
     * 库存余额来源，用于计算总库存、预留量、可用量与仓库分布摘要。
     */
    private final InventoryItemRepository inventoryItemRepository;

    /**
     * 生产计划来源，用于估算成品“在产量”。
     */
    private final ProductionPlanRepository productionPlanRepository;

    /**
     * 采购订单来源，用于估算原材料“在途量”。
     */
    private final PurchaseOrderRepository purchaseOrderRepository;

    /**
     * 采购申请仓库，用于把原材料预警直接转成待采购动作。
     */
    private final PurchaseRequestRepository purchaseRequestRepository;

    /**
     * 当前操作人解析仓库，用于补齐生产计划/采购申请的发起人信息。
     */
    private final UserRepository userRepository;

    /**
     * 站内通知服务，负责把预警触发后的动作广播给相关角色看板。
     */
    private final NotificationService notificationService;

    /**
     * 构造器注入全部依赖，便于测试时替换 Repository/通知服务，并保持控制器自身无状态。
     */
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

    /**
     * 汇总所有库存预警。
     *
     * <p>返回结果被分成成品与原材料两组，因为它们的后续处置路径完全不同：前者需要生产部门新增生产计划，
     * 后者需要采购部门补齐采购申请。这里仅返回“真实缺口大于 0”的项目，避免前端看到大量不需要动作的数据。</p>
     */
    @GetMapping
    public InventoryAlertSummaryView listAlerts() {

        // 成品与原材料分开聚合，不只是为了前端展示方便，
        // 更重要的是二者后续动作不同：一个走生产计划，一个走采购申请。
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

    /**
     * 根据成品预警一键生成生产计划。
     *
     * <p>数量优先采用前端传入值；若未传或非法，则回退为预警计算出的推荐数量。该接口本质上把“风险识别”
     * 直接转成“执行对象”，缩短仓库与生产之间的沟通路径。保存后会向生产相关主题广播，让生产看板实时刷新。</p>
     */
    @PostMapping("/finished-goods/{productId}/production-plan")
    public ProductionPlan createProductionPlan(@PathVariable Long productId,
                                               @RequestBody(required = false) AlertActionRequest request,
                                               Authentication authentication) {
        Product product = requireProduct(productId, "FINISHED_GOOD");
        User operator = requireCurrentUser(authentication);
        InventoryAlertItemView alert = buildAlert(product, true);

        // 允许前端覆盖推荐值，体现“系统建议 + 人工确认”的协同模式；
        // 若前端不传或传非法值，则回退到系统计算结果。
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

        // 同时广播到与生产相关的多个主题，原因是当前前端存在不同看板入口；
        // 重复广播换来的是订阅端结构更简单，而不是要求所有页面都订阅同一个总主题后再自行过滤。
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

    /**
     * 根据原材料预警一键生成采购申请。
     *
     * <p>与成品自动建计划的思路一致，但输出对象改为采购申请。这里没有直接创建采购单，而是先进入采购申请，
     * 说明系统在流程上仍保留采购经理审核/合单/选供应商这一步，避免仓库预警直接越权下采购单。</p>
     */
    @PostMapping("/raw-materials/{productId}/purchase-request")
    public PurchaseRequest createPurchaseRequest(@PathVariable Long productId,
                                                 @RequestBody(required = false) AlertActionRequest request,
                                                 Authentication authentication) {
        Product product = requireProduct(productId, "RAW_MATERIAL");
        User operator = requireCurrentUser(authentication);
        InventoryAlertItemView alert = buildAlert(product, false);

        // 与生产计划接口保持相同约定：优先用人工修正数量，缺失时自动回退推荐值。
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

        // 若前端未填写说明，则系统自动把“这是库存预警触发的补料动作”写入备注，
        // 帮助采购侧快速理解来源，不必再人工追问仓库。
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

    /**
     * 构建单个产品的库存预警视图。
     *
     * <p>预警核心公式：</p>
     * <ul>
     *   <li>可用库存 = 实际库存 - 已预留库存</li>
     *   <li>缺口数量 = max(0, 安全库存 - 可用库存)</li>
     *   <li>推荐动作数量 = max(0, 安全库存 - 可用库存 - 在途/在产量)</li>
     * </ul>
     *
     * <p>注意：严重级别按“当前可用库存”分层，而不是按推荐动作数量分层。也就是说，即便已有在途补货，
     * 当前仓里已经接近 0 时仍会被标记为高危，这更符合仓库现场的风险感知。</p>
     */
    private InventoryAlertItemView buildAlert(Product product, boolean finishedGood) {

        // 这里多次调用 findByProductId，逻辑上清晰但存在重复查库；
        // 如果预警产品很多，后续可改成一次性取出库存列表后在内存中复用或改为聚合 SQL。
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

        // 风险级别看的是“现在仓里有多危险”，不是“建议动作还差多少”；
        // 即使已有在途/在产补充，只要当前几乎没货，仓库现场仍应看到高危提示。
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

    /**
     * 统计某成品尚未完工/未入库的在产数量。
     *
     * <p>这部分数量代表未来短期供给，因此会冲减推荐补产数量。但这里只按计划量累计，没有扣除已完成比例，
     * 属于较粗粒度估算，更适合预警场景而非精确 MRP。</p>
     */
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

    /**
     * 统计某原材料处于采购流程中的在途数量。
     *
     * <p>仅计算“已发出采购但尚未仓库确认入库”的状态，避免把已入库数量重复计入可用库存。这里直接复用采购
     * 工作流中的状态常量，体现了库存预警对采购流程定义的依赖关系。</p>
     */
    private double inTransitQuantity(Long productId) {
        return purchaseOrderRepository.findAll().stream()

                // 这里只统计“采购流程中但尚未仓库确认入库”的状态，
                // 否则已入库订单会和库存余额重复计算。
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

    /**
     * 校验产品存在且类型匹配。
     *
     * <p>这能防止把成品预警接口拿去处理原材料，或把原材料补料接口误用于成品，保证动作路径与产品类型一致。</p>
     */
    private Product requireProduct(Long productId, String productType) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "产品不存在: " + productId));
        if (!productType.equalsIgnoreCase(safe(product.getProductType()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品类型不匹配");
        }
        return product;
    }

    /**
     * 严格解析当前操作人，预警动作类接口必须能追溯是谁创建了生产计划或采购申请。
     */
    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作"));
    }

    /**
     * 生成展示用姓名，优先真人姓名，其次邮箱，提升看板可读性。
     */
    private String resolveDisplayName(User user) {
        return user == null ? "" : (isBlank(user.getName()) ? safe(user.getEmail()) : user.getName());
    }

    /**
     * 生成“仓库名 + 可用量”的摘要文本，让预警列表无需展开详情就能看到库存分布概况。
     */
    private String formatWarehouseLine(InventoryItem item) {
        String warehouseName = item.getWarehouse() == null ? "未分仓" : safe(item.getWarehouse().getName());
        return warehouseName + " 可用 " + String.format(Locale.ROOT, "%.2f", Math.max(0.0, safe(item.getQuantity()) - safe(item.getReservedQuantity())));
    }

    /**
     * 生成由库存预警触发的采购申请编号，与常规采购申请在编码层做来源区分。
     */
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

    /**
     * 预警接口的聚合返回对象。
     *
     * <p>将成品与原材料分组返回而非平铺列表，能让前端天然按“生产动作”和“采购动作”拆面板展示，
     * 同时保留生成时间，方便看板判断数据是否新鲜。</p>
     */
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

    /**
     * 单条库存预警视图。
     *
     * <p>这是一个面向前端的只读 DTO，字段同时覆盖“现状数据、风险结果、建议动作、仓库摘要、供应商提示”，
     * 其目的不是完全映射数据库，而是给看板一次性返回可直接渲染的业务语义。</p>
     */
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

    /**
     * 预警动作请求体。
     *
     * <p>允许前端覆盖系统建议数量，并附带人工备注，实现“系统推荐 + 人工校正”的半自动化流程。</p>
     */
    public static class AlertActionRequest {
        private Double quantity;
        private String note;

        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}

