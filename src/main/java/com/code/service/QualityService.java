package com.code.service;

import com.code.entity.Batch;
import com.code.entity.QualityRecord;
import com.code.entity.SalesOrder;
import com.code.entity.User;
import com.code.repository.BatchRepository;
import com.code.repository.QualityRecordRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.UserRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class QualityService {

    public static final String STATUS_PENDING = "待检";
    public static final String STATUS_PASSED = "合格";
    public static final String STATUS_FAILED = "不合格";

    private final BatchRepository batchRepository;
    private final QualityRecordRepository qualityRecordRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public QualityService(BatchRepository batchRepository,
                          QualityRecordRepository qualityRecordRepository,
                          SalesOrderRepository salesOrderRepository,
                          UserRepository userRepository,
                          NotificationService notificationService) {
        this.batchRepository = batchRepository;
        this.qualityRecordRepository = qualityRecordRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public List<Batch> listBatches(String keyword, String qualityStatus) {
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = normalizeStatus(qualityStatus);
        return batchRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(batch -> normalizedStatus.isEmpty() || normalizeStatus(batch.getQualityStatus()).equals(normalizedStatus))
                .filter(batch -> normalizedKeyword.isEmpty() || matchesKeyword(batch, normalizedKeyword))
                .collect(Collectors.toList());
    }

    public Batch getBatchByNo(String batchNo) {
        if (isBlank(batchNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批次号不能为空");
        }
        return batchRepository.findByBatchNoIgnoreCase(batchNo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在: " + batchNo));
    }

    public List<QualityRecord> listRecords(Long batchId) {
        requireBatch(batchId);
        return qualityRecordRepository.findByBatchIdOrderByInspectionDateDesc(batchId);
    }

    public List<QualityRecord> listMyRecords(String inspectorEmail, String keyword, String result) {
        User inspector = resolveInspector(inspectorEmail);
        if (inspector == null || inspector.getId() == null) {
            return List.of();
        }
        String normalizedKeyword = normalize(keyword);
        String normalizedResult = normalizeStatus(result);
        return qualityRecordRepository.findByInspectorOrderByInspectionDateDesc(inspector.getId()).stream()
                .filter(record -> normalizedResult.isEmpty() || normalizeStatus(record.getResult()).equals(normalizedResult))
                .filter(record -> normalizedKeyword.isEmpty() || matchesRecordKeyword(record, normalizedKeyword))
                .collect(Collectors.toList());
    }

    public List<Batch> listProductionAlerts(String productionManagerEmail) {
        if (isBlank(productionManagerEmail)) {
            return List.of();
        }
        return batchRepository.findByProductionManagerEmailIgnoreCaseAndQualityStatusOrderByQualityInspectedAtDesc(
                productionManagerEmail.trim(),
                STATUS_FAILED
        );
    }

    @Transactional
    public Batch inspectBatch(Long batchId, String result, String remarks, String inspectorEmail) {
        Batch batch = requireBatch(batchId);
        String normalizedResult = normalizeResult(result);
        User inspector = resolveInspector(inspectorEmail);
        if (!STATUS_PENDING.equals(safe(batch.getQualityStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该批次已完成质检，如需返工请由生产管理员重新完工后再检验");
        }
        if (STATUS_FAILED.equals(normalizedResult) && isBlank(remarks)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不合格时请填写具体原因");
        }

        QualityRecord record = new QualityRecord();
        record.setBatch(batch);
        record.setProduct(batch.getProduct());
        record.setInspector(inspector == null ? null : inspector.getId());
        record.setInspectorName(inspector == null ? safe(inspectorEmail) : inspector.getName());
        record.setInspectionDate(LocalDateTime.now());
        record.setResult(normalizedResult);
        record.setRemarks(blankToNull(remarks));

        batch.setQualityStatus(normalizedResult);
        batch.setQualityRemark(blankToNull(remarks));
        batch.setQualityInspectedAt(record.getInspectionDate());
        batch.setQualityInspectorId(record.getInspector());
        batch.setQualityInspectorName(record.getInspectorName());

        if (STATUS_FAILED.equals(normalizedResult) && !isBlank(batch.getProductionManagerEmail())) {
            record.setNotificationSent(true);
            record.setNotifiedProductionManagerEmail(batch.getProductionManagerEmail());
            record.setNotifiedAt(LocalDateTime.now());
            notifyProductionManager(batch, record);
        }

        qualityRecordRepository.save(record);
        Batch saved = batchRepository.save(batch);
        updateRelatedOrderStatus(saved, normalizedResult, remarks, inspectorEmail);
        notificationService.broadcast(
                "/topic/quality",
                new NotificationMessage(
                        STATUS_FAILED.equals(normalizedResult) ? "QUALITY_REWORK_REQUIRED" : "QUALITY_PASSED",
                        "Batch",
                        saved.getId(),
                        new QualityNoticePayload(saved,
                                STATUS_FAILED.equals(normalizedResult)
                                        ? "批次 " + saved.getBatchNo() + " 质检不合格，已通知生产管理员。"
                                        : "批次 " + saved.getBatchNo() + " 质检合格。",
                                blankToNull(remarks)),
                        LocalDateTime.now()
                )
        );
        return saved;
    }

    private void notifyProductionManager(Batch batch, QualityRecord record) {
        String topic = resolveProductionManagerTopic(batch.getProductionManagerEmail());
        notificationService.broadcast(
                topic,
                new NotificationMessage(
                        "QUALITY_REWORK_REQUIRED",
                        "Batch",
                        batch.getId(),
                        new QualityNoticePayload(
                                batch,
                                "您负责的批次 " + batch.getBatchNo() + " 质检不合格，请查看！",
                                blankToNull(record.getRemarks()) == null ? safe(batch.getProduct() == null ? null : batch.getProduct().getName()) : record.getRemarks()
                        ),
                        LocalDateTime.now()
                )
        );
    }

    private void notifyWarehouseForQualifiedBatch(Batch batch, String remarks) {
        notificationService.broadcast(
                "/topic/orders/warehouse",
                new NotificationMessage(
                        "QUALITY_PASSED_PENDING_STOCK_IN",
                        "Batch",
                        batch.getId(),
                        new QualityNoticePayload(
                                batch,
                                "批次 " + batch.getBatchNo() + " 质检合格，请仓库确认入库。",
                                blankToNull(remarks) == null ? safe(batch.getSourceOrderNo()) : remarks
                        ),
                        LocalDateTime.now()
                )
        );
    }

    private void updateRelatedOrderStatus(Batch batch, String normalizedResult, String remarks, String inspectorEmail) {
        if (isBlank(batch.getSourceOrderNo())) {
            return;
        }
        SalesOrder order = salesOrderRepository.findAll().stream()
                .filter(item -> item.getOrderNo() != null && item.getOrderNo().equalsIgnoreCase(batch.getSourceOrderNo()))
                .findFirst()
                .orElse(null);
        if (order == null) {
            return;
        }
        if (STATUS_FAILED.equals(normalizedResult)) {
            order.setStatus(OrderWorkflowService.STATUS_IN_PRODUCTION);
            salesOrderRepository.save(order);
            notificationService.broadcast(
                    "/topic/orders",
                    new NotificationMessage(
                            "ORDER_BACK_TO_PRODUCTION",
                            "SalesOrder",
                            order.getId(),
                            new QualityNoticePayload(batch,
                                    "订单 " + order.getOrderNo() + " 对应批次质检不合格，已退回生产。",
                                    blankToNull(remarks) == null ? safe(inspectorEmail) : remarks),
                            LocalDateTime.now()
                    )
            );
            return;
        }

        List<Batch> orderBatches = batchRepository.findBySourceOrderNoIgnoreCaseOrderByCreatedAtDesc(order.getOrderNo());
        boolean allPassed = !orderBatches.isEmpty() && orderBatches.stream()
                .allMatch(item -> STATUS_PASSED.equals(safe(item.getQualityStatus())));
        if (allPassed) {
            order.setStatus(OrderWorkflowService.STATUS_PENDING_PRODUCTION_STOCK_IN);
            salesOrderRepository.save(order);
            notifyWarehouseForQualifiedBatch(batch, remarks);
            notificationService.broadcast(
                    "/topic/orders",
                    new NotificationMessage(
                            "ORDER_READY_FOR_PRODUCTION_STOCK_IN",
                            "SalesOrder",
                            order.getId(),
                            new QualityNoticePayload(batch,
                                    "订单 " + order.getOrderNo() + " 对应成品质检完成，等待仓库入库。",
                                    blankToNull(remarks)),
                            LocalDateTime.now()
                    )
            );
        }
    }

    private Batch requireBatch(Long batchId) {
        if (batchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批次ID不能为空");
        }
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在: " + batchId));
    }

    private User resolveInspector(String inspectorEmail) {
        if (isBlank(inspectorEmail)) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(inspectorEmail.trim()).orElse(null);
    }

    private boolean matchesKeyword(Batch batch, String keyword) {
        return contains(batch.getBatchNo(), keyword)
                || contains(batch.getSourceOrderNo(), keyword)
                || contains(batch.getQualityStatus(), keyword)
                || contains(batch.getQualityRemark(), keyword)
                || contains(batch.getQualityInspectorName(), keyword)
                || contains(batch.getProductionManagerName(), keyword)
                || contains(batch.getProductionManagerEmail(), keyword)
                || contains(batch.getProduct() == null ? null : batch.getProduct().getName(), keyword)
                || contains(batch.getProduct() == null ? null : batch.getProduct().getSku(), keyword);
    }

    private boolean matchesRecordKeyword(QualityRecord record, String keyword) {
        return contains(record.getInspectorName(), keyword)
                || contains(record.getResult(), keyword)
                || contains(record.getRemarks(), keyword)
                || contains(record.getBatch() == null ? null : record.getBatch().getBatchNo(), keyword)
                || contains(record.getBatch() == null ? null : record.getBatch().getSourceOrderNo(), keyword)
                || contains(record.getProduct() == null ? null : record.getProduct().getName(), keyword)
                || contains(record.getProduct() == null ? null : record.getProduct().getSku(), keyword);
    }

    private String resolveProductionManagerTopic(String email) {
        String normalized = safe(email).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "/topic/production" : "/topic/production/manager/" + normalized;
    }

    private String normalizeResult(String result) {
        String normalized = safe(result).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先选择质检结果");
        }
        if (List.of("PASS", "PASSED", "QUALIFIED", "合格").contains(normalized)) {
            return STATUS_PASSED;
        }
        if (List.of("FAIL", "FAILED", "UNQUALIFIED", "不合格").contains(normalized)) {
            return STATUS_FAILED;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "质检结果仅支持 合格 或 不合格");
    }

    private String normalizeStatus(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private boolean contains(String source, String keyword) {
        return normalize(source).contains(keyword);
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class QualityNoticePayload {
        private final Batch batch;
        private final String notificationTitle;
        private final String notificationMeta;

        public QualityNoticePayload(Batch batch, String notificationTitle, String notificationMeta) {
            this.batch = batch;
            this.notificationTitle = notificationTitle;
            this.notificationMeta = notificationMeta;
        }

        public Batch getBatch() {
            return batch;
        }

        public String getNotificationTitle() {
            return notificationTitle;
        }

        public String getNotificationMeta() {
            return notificationMeta;
        }
    }
}

