package com.code.controller;

import com.code.entity.Batch;
import com.code.entity.ProductionMaterialRequest;
import com.code.entity.ProductionPlan;
import com.code.entity.ProductionTask;
import com.code.entity.ProductionWeeklyPlan;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.ProductionTaskRepository;
import com.code.service.OrderWorkflowService;
import com.code.service.ProductionMaterialRequestService;
import com.code.service.QualityService;
import com.code.service.WeeklyPlanningService;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/production")
/*
 * 生产模块控制器。
 *
 * <p>该类负责暴露生产任务、生产记录、领料申请、质量预警、周计划等与生产执行相关的 API。
 * 它在系统中的作用是承接“生产管理员视角”的核心工作台能力：
 * 既能看任务、回传状态，也能查看历史生产记录和质量异常，属于典型的生产执行层接口。</p>
 *
 * <p>设计上，该 Controller 更多扮演“查询编排 + 权限边界 + Service 调用入口”的角色，
 * 复杂的业务落在 WeeklyPlanningService、ProductionMaterialRequestService、QualityService 等服务中。</p>
 */
public class ProductionController {

    @Autowired
    private ProductionTaskRepository productionTaskRepository;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private QualityService qualityService;

    @Autowired
    private WeeklyPlanningService weeklyPlanningService;

    @Autowired
    private ProductionMaterialRequestService productionMaterialRequestService;

    @Autowired
    private OrderWorkflowService orderWorkflowService;

    @PostMapping("/tasks")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    /*
     * 创建生产任务。
     *
     * <p>当前实现比较直接：接收前端传入的 ProductionTask 实体后直接保存，
     * 并通过 WebSocket 广播通知生产看板刷新。</p>
     *
     * <p>这种实现适合快速交付，但在企业级场景下通常还会补充：
     * 任务编号生成规范、状态机校验、来源订单/计划一致性校验等。</p>
     */
    public ProductionTask createTask(@RequestBody ProductionTask task) {
        ProductionTask saved = productionTaskRepository.save(task);
        notificationService.broadcast("/topic/production", new NotificationMessage("TASK_CREATED","ProductionTask", saved.getId(), saved, null));
        return saved;
    }

    @GetMapping("/tasks/user/{userId}")
    /*
     * 查询指定人员名下的生产任务。
     *
     * <p>这个接口面向“按人看任务”场景，适合生产管理员或个人工作台展示。</p>
     */
    public List<ProductionTask> listByUser(@PathVariable Long userId) {
        return productionTaskRepository.findByAssignedTo(userId);
    }

    @GetMapping("/records")
    /*
     * 查询生产记录。
     *
     * <p>普通生产管理员默认只看自己的生产完成记录；
     * 管理员可以看全量。
     * 这里的“生产记录”并不是独立表，而是从 production_plan 中筛选出 DONE/WAREHOUSED 状态的数据进行投影。</p>
     */
    public List<ProductionRecordView> listRecords(@RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false) String keyword,
                                                  Authentication authentication) {
        return queryRecords(startDate, endDate, keyword, authentication, false);
    }

    @GetMapping("/plans/active")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    /*
     * 查询待执行生产计划。
     *
     * <p>这里返回的是“尚未完工/未入库”的生产计划单，既包括订单缺货触发的生产计划，
     * 也包括仓库库存预警一键补产生成的计划。之所以单独开放该查询，是因为这类计划在当前系统中
     * 还不等同于销售订单，但生产管理员仍需要一个清晰入口看到新增待办。</p>
     */
    public List<ActiveProductionPlanView> listActivePlans() {
        return productionPlanRepository.findAll().stream()
                .filter(plan -> !isProductionRecord(plan.getStatus()))
                .sorted(Comparator.comparing(ProductionPlan::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductionPlan::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toActivePlanView)
                .collect(Collectors.toList());
    }

    @GetMapping("/plans/pending-stock-in")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public List<ActiveProductionPlanView> listPendingInventoryAlertStockInPlans() {
        return orderWorkflowService.listInventoryAlertPlansPendingStockIn().stream()
                .map(this::toActivePlanView)
                .collect(Collectors.toList());
    }

    @PostMapping("/plans/{planId}/complete")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public ProductionPlan completeInventoryAlertPlan(@PathVariable Long planId,
                                                     @RequestBody(required = false) PlanActionCommand request,
                                                     Authentication authentication) {
        return orderWorkflowService.completeInventoryAlertPlan(
                planId,
                authentication == null ? "" : authentication.getName(),
                request == null ? "" : request.getNote()
        );
    }

    @PostMapping("/plans/{planId}/warehouse-stock-in")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ProductionPlan confirmInventoryAlertPlanStockIn(@PathVariable Long planId,
                                                           @RequestBody(required = false) WarehouseReviewCommand request,
                                                           Authentication authentication) {
        return orderWorkflowService.confirmInventoryAlertPlanStockIn(
                planId,
                authentication == null ? "" : authentication.getName(),
                request == null ? "" : request.getNote()
        );
    }

    @GetMapping("/material-requests")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','WAREHOUSE_MANAGER','PROCUREMENT_MANAGER','ADMIN')")
    /*
     * 查询生产领料申请。
     *
     * <p>这是典型的跨角色协同接口：
     * - 生产管理员提交领料；
     * - 仓库管理员审核出库；
     * - 采购管理员查看是否存在缺料；
     * 因此授权范围覆盖多个岗位。</p>
     */
    public List<ProductionMaterialRequest> listMaterialRequests(@RequestParam(required = false) Long orderId,
                                                                @RequestParam(required = false) Long planId,
                                                                @RequestParam(required = false) String status,
                                                                Authentication authentication) {
        String role = authentication == null || authentication.getAuthorities() == null
                ? ""
                : authentication.getAuthorities().stream().findFirst().map(authority -> authority == null ? "" : authority.getAuthority()).orElse("");
        String email = authentication == null ? "" : authentication.getName();
        return productionMaterialRequestService.listRequests(role, email, orderId, planId, status);
    }

    @PostMapping("/material-requests")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    /*
     * 创建生产领料申请。
     *
     * <p>Controller 层把前端请求对象转换为服务层更稳定的命令对象，
     * 这样可以降低 Service 对 Web 请求结构的耦合度。</p>
     */
    public ProductionMaterialRequest createMaterialRequest(@RequestBody MaterialRequestCommand request,
                                                           Authentication authentication) {
        List<ProductionMaterialRequestService.MaterialItemCommand> items = request == null || request.getItems() == null
                ? List.of()
                : request.getItems().stream()
                .map(item -> new ProductionMaterialRequestService.MaterialItemCommand(item.getMaterialProductId(), item.getRequiredQuantity()))
                .collect(Collectors.toList());
        return productionMaterialRequestService.createRequest(
                request == null ? null : request.getOrderId(),
                request == null ? null : request.getPlanId(),
                items,
                request == null ? "" : request.getNote(),
                authentication == null ? "" : authentication.getName()
        );
    }

    @PostMapping("/material-requests/{requestId}/warehouse-review")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    /*
     * 仓库对生产领料申请做审核/出库处理。
     *
     * <p>该接口通常会联动库存扣减和库存流水生成，
     * 因此必须由仓库角色执行，而不能由生产侧直接修改库存。</p>
     */
    public ProductionMaterialRequest warehouseReviewMaterialRequest(@PathVariable Long requestId,
                                                                    @RequestBody(required = false) WarehouseReviewCommand request,
                                                                    Authentication authentication) {
        return productionMaterialRequestService.warehouseReview(
                requestId,
                request == null ? "" : request.getNote(),
                authentication == null ? "" : authentication.getName()
        );
    }

    @GetMapping("/records/overview")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    /*
     * 查询生产记录总览。
     *
     * <p>与 listRecords 的区别在于：overview 允许返回全员记录，
     * 主要用于图表、统计面板等“团队级”分析场景。</p>
     */
    public List<ProductionRecordView> listRecordOverview(@RequestParam(required = false) String startDate,
                                                         @RequestParam(required = false) String endDate,
                                                         @RequestParam(required = false) String keyword,
                                                         Authentication authentication) {
        return queryRecords(startDate, endDate, keyword, authentication, true);
    }

    private List<ProductionRecordView> queryRecords(String startDate,
                                                    String endDate,
                                                    String keyword,
                                                    Authentication authentication,
                                                    boolean includeAllOperators) {
        // 统一的生产记录查询实现：
        // 1. 解析时间参数；
        // 2. 判断当前用户是否管理员；
        // 3. 从生产计划中筛选出“构成记录”的状态；
        // 4. 再按人、按时间、按关键字过滤；
        // 5. 最后映射成前端需要的记录视图对象。
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        String normalizedKeyword = normalize(keyword);
        boolean admin = hasRole(authentication, "ROLE_ADMIN");
        String currentEmail = authentication == null ? "" : normalize(authentication.getName());

        return productionPlanRepository.findAll().stream()
                // 生产记录的来源是 production_plan，但不是所有计划都算“已形成记录”，
                // 只有 DONE/WAREHOUSED 才说明生产已经实质完成到一定阶段。
                .filter(plan -> isProductionRecord(plan.getStatus()))

                // includeAllOperators = true 时用于全员统计，不按当前操作人过滤；
                // 否则普通生产管理员只能看到自己的完工记录。
                .filter(plan -> includeAllOperators || admin || currentEmail.isEmpty() || currentEmail.equals(normalize(plan.getCompletedByEmail())))
                .filter(plan -> start == null || (resolveCompletedAt(plan) != null && !resolveCompletedAt(plan).isBefore(start)))
                .filter(plan -> end == null || (resolveCompletedAt(plan) != null && !resolveCompletedAt(plan).isAfter(end)))
                .map(this::toRecordView)
                .filter(record -> normalizedKeyword.isEmpty() || matchesKeyword(record, normalizedKeyword))
                .sorted(Comparator.comparing(ProductionRecordView::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductionRecordView::getPlanNo, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @GetMapping("/quality-alerts")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    /*
     * 查询生产相关质量预警。
     *
     * <p>当批次质检不合格时，生产管理员需要及时感知返工任务，
     * 因此这里按生产负责人邮箱聚合不合格批次。</p>
     */
    public List<ProductionQualityAlertView> listQualityAlerts(Authentication authentication) {
        String email = authentication == null ? "" : authentication.getName();
        return qualityService.listProductionAlerts(email).stream()
                .map(this::toQualityAlertView)
                .collect(Collectors.toList());
    }

    @GetMapping("/weekly-plans")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    /*
     * 查询全部生产周计划。
     * 用于计划列表、历史周计划查看等场景。
     */
    public List<ProductionWeeklyPlan> listWeeklyPlans() {
        return weeklyPlanningService.listProductionPlans();
    }

    @GetMapping("/weekly-plans/current")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    /*
     * 查询当前参考日期对应的生产周计划。
     * 如果不存在，服务层可能会按规则自动生成。
     */
    public ProductionWeeklyPlan getCurrentWeeklyPlan(@RequestParam(required = false) String referenceDate,
                                                     Authentication authentication) {
        return weeklyPlanningService.getOrGenerateProductionPlan(parseReferenceDate(referenceDate), authentication == null ? "" : authentication.getName());
    }

    @PostMapping("/weekly-plans/generate")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    /*
     * 强制重新生成生产周计划。
     *
     * <p>这个接口常用于“计划快照失效”或“希望基于最新业务数据重算”的场景，
     * 与单纯刷新页面不同，它会真正触发服务层的重新计算逻辑。</p>
     */
    public ProductionWeeklyPlan generateWeeklyPlan(@RequestParam(required = false) String referenceDate,
                                                   Authentication authentication) {
        return weeklyPlanningService.generateProductionPlan(parseReferenceDate(referenceDate), authentication == null ? "" : authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    /*
     * 更新生产任务状态。
     *
     * <p>当前实现是直接改状态字段并广播通知，逻辑比较轻。
     * 如果未来任务状态流转更复杂，建议引入明确的状态机约束，防止非法跳转。</p>
     */
    public ProductionTask updateStatus(@PathVariable Long taskId, @RequestParam String status) {
        ProductionTask t = productionTaskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        t.setStatus(status);
        ProductionTask saved = productionTaskRepository.save(t);
        notificationService.broadcast("/topic/production", new NotificationMessage("TASK_UPDATED","ProductionTask", saved.getId(), saved, null));
        return saved;
    }

    private boolean isProductionRecord(String status) {
        // DONE / WAREHOUSED 两种状态被视为“可统计生产记录”，
        // 分别代表完工、完工并入库两类生产完成场景。
        String normalized = normalize(status);
        return "done".equals(normalized) || "warehoused".equals(normalized);
    }

    private ProductionRecordView toRecordView(ProductionPlan plan) {
        // 这里做的是“实体 -> 展示视图”转换，
        // 目的是把生产计划聚合成前端真正需要展示的记录结构。
        return new ProductionRecordView(
                plan.getId(),
                plan.getPlanNo(),
                resolveOrderNo(plan.getPlanNo()),
                plan.getProduct() == null ? null : plan.getProduct().getSku(),
                plan.getProduct() == null ? null : plan.getProduct().getName(),
                plan.getPlannedQuantity(),
                plan.getStatus(),
                plan.getCreatedBy(),
                plan.getCreatedByName(),
                plan.getCompletedById(),
                plan.getCompletedByName(),
                plan.getStartDate(),
                resolveCompletedAt(plan),
                plan.getCreatedAt()
        );
    }

    private ActiveProductionPlanView toActivePlanView(ProductionPlan plan) {
        boolean inventoryAlertPlan = isInventoryAlertPlan(plan);
        return new ActiveProductionPlanView(
                plan.getId(),
                plan.getPlanNo(),
                inventoryAlertPlan ? "库存预警补产" : "订单缺货补产",
                inventoryAlertPlan ? "" : resolveOrderNo(plan.getPlanNo()),
                plan.getProduct() == null ? null : plan.getProduct().getSku(),
                plan.getProduct() == null ? null : plan.getProduct().getName(),
                plan.getPlannedQuantity(),
                plan.getStatus(),
                plan.getCreatedBy(),
                plan.getCreatedByName(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getCreatedAt()
        );
    }

    private ProductionQualityAlertView toQualityAlertView(Batch batch) {
        return new ProductionQualityAlertView(
                batch.getId(),
                batch.getBatchNo(),
                batch.getSourceOrderNo(),
                batch.getProduct() == null ? null : batch.getProduct().getSku(),
                batch.getProduct() == null ? null : batch.getProduct().getName(),
                batch.getQuantity(),
                batch.getQualityStatus(),
                batch.getQualityRemark(),
                batch.getQualityInspectorName(),
                batch.getQualityInspectedAt()
        );
    }

    private LocalDateTime resolveCompletedAt(ProductionPlan plan) {
        // 优先使用 endDate 作为完工时间；
        // 若缺失，则退化使用 createdAt，保证记录排序和过滤不至于直接丢失。
        return plan.getEndDate() != null ? plan.getEndDate() : plan.getCreatedAt();
    }

    private boolean hasRole(Authentication authentication, String roleName) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .map(authority -> authority == null ? "" : authority.getAuthority())
                .anyMatch(role -> roleName.equalsIgnoreCase(role));
    }

    private String resolveOrderNo(String planNo) {
        // 生产计划号中包含来源订单号信息，
        // 这里通过字符串拆解把订单号还原出来，供前端展示与检索使用。
        if (planNo == null || !planNo.startsWith("PLAN-")) {
            return "";
        }
        if (planNo.startsWith("PLAN-ALERT-")) {
            return "";
        }
        String remaining = planNo.substring("PLAN-".length());
        int lastSeparator = remaining.lastIndexOf('-');
        if (lastSeparator <= 0) {
            return remaining;
        }
        String withoutTimestamp = remaining.substring(0, lastSeparator);
        int productSeparator = withoutTimestamp.lastIndexOf('-');
        if (productSeparator <= 0) {
            return withoutTimestamp;
        }
        return withoutTimestamp.substring(0, productSeparator);
    }

    private boolean isInventoryAlertPlan(ProductionPlan plan) {
        return plan != null && plan.getPlanNo() != null && plan.getPlanNo().startsWith("PLAN-ALERT-");
    }

    private boolean matchesKeyword(ProductionRecordView record, String keyword) {
        return contains(record.getPlanNo(), keyword)
                || contains(record.getOrderNo(), keyword)
                || contains(record.getProductSku(), keyword)
                || contains(record.getProductName(), keyword)
                || contains(record.getStatus(), keyword);
    }

    private boolean contains(String source, String keyword) {
        return normalize(source).contains(keyword);
    }

    private LocalDate parseReferenceDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参考日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime parseStartDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private LocalDateTime parseEndDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atTime(23, 59, 59);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期格式错误，应为 yyyy-MM-dd");
        }
    }

    public static class ProductionRecordView {
        private final Long id;
        private final String planNo;
        private final String orderNo;
        private final String productSku;
        private final String productName;
        private final Double plannedQuantity;
        private final String status;
        private final Long createdBy;
        private final String createdByName;
        private final Long completedById;
        private final String completedByName;
        private final LocalDateTime startDate;
        private final LocalDateTime completedAt;
        private final LocalDateTime createdAt;

        public ProductionRecordView(Long id,
                                    String planNo,
                                    String orderNo,
                                    String productSku,
                                    String productName,
                                    Double plannedQuantity,
                                    String status,
                                    Long createdBy,
                                    String createdByName,
                                    Long completedById,
                                    String completedByName,
                                    LocalDateTime startDate,
                                    LocalDateTime completedAt,
                                    LocalDateTime createdAt) {
            this.id = id;
            this.planNo = planNo;
            this.orderNo = orderNo;
            this.productSku = productSku;
            this.productName = productName;
            this.plannedQuantity = plannedQuantity;
            this.status = status;
            this.createdBy = createdBy;
            this.createdByName = createdByName;
            this.completedById = completedById;
            this.completedByName = completedByName;
            this.startDate = startDate;
            this.completedAt = completedAt;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getPlanNo() {
            return planNo;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getProductSku() {
            return productSku;
        }

        public String getProductName() {
            return productName;
        }

        public Double getPlannedQuantity() {
            return plannedQuantity;
        }

        public String getStatus() {
            return status;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public String getCreatedByName() {
            return createdByName;
        }

        public Long getCompletedById() {
            return completedById;
        }

        public String getCompletedByName() {
            return completedByName;
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    public static class ActiveProductionPlanView {
        private final Long id;
        private final String planNo;
        private final String sourceType;
        private final String orderNo;
        private final String productSku;
        private final String productName;
        private final Double plannedQuantity;
        private final String status;
        private final Long createdBy;
        private final String createdByName;
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final LocalDateTime createdAt;

        public ActiveProductionPlanView(Long id,
                                        String planNo,
                                        String sourceType,
                                        String orderNo,
                                        String productSku,
                                        String productName,
                                        Double plannedQuantity,
                                        String status,
                                        Long createdBy,
                                        String createdByName,
                                        LocalDateTime startDate,
                                        LocalDateTime endDate,
                                        LocalDateTime createdAt) {
            this.id = id;
            this.planNo = planNo;
            this.sourceType = sourceType;
            this.orderNo = orderNo;
            this.productSku = productSku;
            this.productName = productName;
            this.plannedQuantity = plannedQuantity;
            this.status = status;
            this.createdBy = createdBy;
            this.createdByName = createdByName;
            this.startDate = startDate;
            this.endDate = endDate;
            this.createdAt = createdAt;
        }

        public Long getId() { return id; }
        public String getPlanNo() { return planNo; }
        public String getSourceType() { return sourceType; }
        public String getOrderNo() { return orderNo; }
        public String getProductSku() { return productSku; }
        public String getProductName() { return productName; }
        public Double getPlannedQuantity() { return plannedQuantity; }
        public String getStatus() { return status; }
        public Long getCreatedBy() { return createdBy; }
        public String getCreatedByName() { return createdByName; }
        public LocalDateTime getStartDate() { return startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public static class ProductionQualityAlertView {
        private final Long id;
        private final String batchNo;
        private final String orderNo;
        private final String productSku;
        private final String productName;
        private final Double quantity;
        private final String qualityStatus;
        private final String qualityRemark;
        private final String inspectorName;
        private final LocalDateTime inspectedAt;

        public ProductionQualityAlertView(Long id,
                                          String batchNo,
                                          String orderNo,
                                          String productSku,
                                          String productName,
                                          Double quantity,
                                          String qualityStatus,
                                          String qualityRemark,
                                          String inspectorName,
                                          LocalDateTime inspectedAt) {
            this.id = id;
            this.batchNo = batchNo;
            this.orderNo = orderNo;
            this.productSku = productSku;
            this.productName = productName;
            this.quantity = quantity;
            this.qualityStatus = qualityStatus;
            this.qualityRemark = qualityRemark;
            this.inspectorName = inspectorName;
            this.inspectedAt = inspectedAt;
        }

        public Long getId() {
            return id;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getProductSku() {
            return productSku;
        }

        public String getProductName() {
            return productName;
        }

        public Double getQuantity() {
            return quantity;
        }

        public String getQualityStatus() {
            return qualityStatus;
        }

        public String getQualityRemark() {
            return qualityRemark;
        }

        public String getInspectorName() {
            return inspectorName;
        }

        public LocalDateTime getInspectedAt() {
            return inspectedAt;
        }
    }

    public static class MaterialRequestCommand {
        private Long orderId;
        private Long planId;
        private String note;
        private List<MaterialItemCommand> items;

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public Long getPlanId() {
            return planId;
        }

        public void setPlanId(Long planId) {
            this.planId = planId;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public List<MaterialItemCommand> getItems() {
            return items;
        }

        public void setItems(List<MaterialItemCommand> items) {
            this.items = items;
        }
    }

    public static class PlanActionCommand {
        private String note;

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public static class MaterialItemCommand {
        private Long materialProductId;
        private Double requiredQuantity;

        public Long getMaterialProductId() {
            return materialProductId;
        }

        public void setMaterialProductId(Long materialProductId) {
            this.materialProductId = materialProductId;
        }

        public Double getRequiredQuantity() {
            return requiredQuantity;
        }

        public void setRequiredQuantity(Double requiredQuantity) {
            this.requiredQuantity = requiredQuantity;
        }
    }

    public static class WarehouseReviewCommand {
        private String note;

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}

