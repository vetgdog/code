package com.code.controller;

import com.code.entity.ProductionPlan;
import com.code.entity.ProductionTask;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.ProductionTaskRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
                                                  @RequestParam(required = false) String keyword) {
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        String normalizedKeyword = normalize(keyword);

        return productionPlanRepository.findAll().stream()
                .filter(plan -> isProductionRecord(plan.getStatus()))
                .filter(plan -> start == null || (resolveCompletedAt(plan) != null && !resolveCompletedAt(plan).isBefore(start)))
                .filter(plan -> end == null || (resolveCompletedAt(plan) != null && !resolveCompletedAt(plan).isAfter(end)))
                .map(this::toRecordView)
                .filter(record -> normalizedKeyword.isEmpty() || matchesKeyword(record, normalizedKeyword))
                .sorted(Comparator.comparing(ProductionRecordView::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductionRecordView::getPlanNo, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
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
                plan.getStartDate(),
                resolveCompletedAt(plan),
                plan.getCreatedAt()
        );
    }

    private LocalDateTime resolveCompletedAt(ProductionPlan plan) {
        return plan.getEndDate() != null ? plan.getEndDate() : plan.getCreatedAt();
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
}

