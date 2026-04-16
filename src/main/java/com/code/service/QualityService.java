package com.code.service;

import com.code.entity.Batch;
import com.code.entity.QualityRecord;
import com.code.entity.User;
import com.code.repository.BatchRepository;
import com.code.repository.QualityRecordRepository;
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
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public QualityService(BatchRepository batchRepository,
                          QualityRecordRepository qualityRecordRepository,
                          UserRepository userRepository,
                          NotificationService notificationService) {
        this.batchRepository = batchRepository;
        this.qualityRecordRepository = qualityRecordRepository;
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

