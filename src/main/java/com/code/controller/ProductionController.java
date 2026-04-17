package com.code.controller;

import com.code.entity.Batch;
import com.code.entity.ProductionPlan;
import com.code.entity.ProductionTask;
import com.code.entity.ProductionWeeklyPlan;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.ProductionTaskRepository;
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

    @PostMapping("/tasks")
    public ProductionTask createTask(@RequestBody ProductionTask task) {
        ProductionTask saved = productionTaskRepository.save(task);
        notificationService.broadcast("/topic/production", new NotificationMessage("TASK_CREATED","ProductionTask", saved.getId(), saved, null));
        return saved;
    }

    @GetMapping("/tasks/user/{userId}")
    public List<ProductionTask> listByUser(@PathVariable Long userId) {
        return productionTaskRepository.findByAssignedTo(userId);
    }

    @GetMapping("/records")
    public List<ProductionRecordView> listRecords(@RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false) String keyword,
                                                  Authentication authentication) {
        return queryRecords(startDate, endDate, keyword, authentication, false);
    }

    @GetMapping("/records/overview")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
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
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        String normalizedKeyword = normalize(keyword);
        boolean admin = hasRole(authentication, "ROLE_ADMIN");
        String currentEmail = authentication == null ? "" : normalize(authentication.getName());

        return productionPlanRepository.findAll().stream()
                .filter(plan -> isProductionRecord(plan.getStatus()))
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
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    public List<ProductionQualityAlertView> listQualityAlerts(Authentication authentication) {
        String email = authentication == null ? "" : authentication.getName();
        return qualityService.listProductionAlerts(email).stream()
                .map(this::toQualityAlertView)
                .collect(Collectors.toList());
    }

    @GetMapping("/weekly-plans")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    public List<ProductionWeeklyPlan> listWeeklyPlans() {
        return weeklyPlanningService.listProductionPlans();
    }

    @GetMapping("/weekly-plans/current")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    public ProductionWeeklyPlan getCurrentWeeklyPlan(@RequestParam(required = false) String referenceDate,
                                                     Authentication authentication) {
        return weeklyPlanningService.getOrGenerateProductionPlan(parseReferenceDate(referenceDate), authentication == null ? "" : authentication.getName());
    }

    @PostMapping("/weekly-plans/generate")
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER','ADMIN')")
    public ProductionWeeklyPlan generateWeeklyPlan(@RequestParam(required = false) String referenceDate,
                                                   Authentication authentication) {
        return weeklyPlanningService.generateProductionPlan(parseReferenceDate(referenceDate), authentication == null ? "" : authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/status")
    public ProductionTask updateStatus(@PathVariable Long taskId, @RequestParam String status) {
        ProductionTask t = productionTaskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        t.setStatus(status);
        ProductionTask saved = productionTaskRepository.save(t);
        notificationService.broadcast("/topic/production", new NotificationMessage("TASK_UPDATED","ProductionTask", saved.getId(), saved, null));
        return saved;
    }

    private boolean isProductionRecord(String status) {
        String normalized = normalize(status);
        return "done".equals(normalized) || "warehoused".equals(normalized);
    }

    private ProductionRecordView toRecordView(ProductionPlan plan) {
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
        if (planNo == null || !planNo.startsWith("PLAN-")) {
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
}

