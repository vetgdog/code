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
/*
 * 质量服务。
 *
 * <p>该类位于“质检 / 批次追溯”模块，核心职责是围绕 Batch 和 QualityRecord 管理生产批次的检验流程。
 * 它不仅负责保存质检结果，还负责把质检结果反向驱动订单状态、生产返工、仓库待入库提醒等后续业务动作。</p>
 *
 * <p>从架构设计上看，它是一个典型的领域服务（Domain Service）：
 * 因为“质检结果如何影响订单与生产”并不只属于某一个实体自身，而是跨 Batch、QualityRecord、SalesOrder、Notification 的协同规则。</p>
 */
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
        // 批次列表支持按关键字和质检状态过滤，便于质检员快速定位目标批次。
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = normalizeStatus(qualityStatus);
        return batchRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(batch -> normalizedStatus.isEmpty() || normalizeStatus(batch.getQualityStatus()).equals(normalizedStatus))
                .filter(batch -> normalizedKeyword.isEmpty() || matchesKeyword(batch, normalizedKeyword))
                .collect(Collectors.toList());
    }

    public Batch getBatchByNo(String batchNo) {
        // 通过批次号查询比通过主键更符合业务语言，
        // 因为质检、追溯、仓库通常都是按批次号沟通而不是数据库 ID。
        if (isBlank(batchNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批次号不能为空");
        }
        return batchRepository.findByBatchNoIgnoreCase(batchNo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在: " + batchNo));
    }

    public List<QualityRecord> listRecords(Long batchId) {
        // 查询某个批次的全部质检记录，用于批次详情页展示历史检验轨迹。
        requireBatch(batchId);
        return qualityRecordRepository.findByBatchIdOrderByInspectionDateDesc(batchId);
    }

    public List<QualityRecord> listMyRecords(String inspectorEmail, String keyword, String result) {
        // “我的质检记录”场景：先根据当前登录邮箱解析检验员，再按人过滤记录。
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
        // 质量预警面向生产经理，重点展示“质检不合格”的批次，驱动返工处理。
        if (isBlank(productionManagerEmail)) {
            return List.of();
        }
        return batchRepository.findByProductionManagerEmailIgnoreCaseAndQualityStatusOrderByQualityInspectedAtDesc(
                productionManagerEmail.trim(),
                STATUS_FAILED
        );
    }

    @Transactional
    /*
     * 执行批次质检。
     *
     * <p>这是质量模块最核心的方法，承担以下职责：
     * 1. 校验批次当前是否允许质检；
     * 2. 创建质检记录；
     * 3. 更新批次质检状态；
     * 4. 若不合格则通知生产返工；
     * 5. 若合格则推动订单进入待生产入库状态并通知仓库。</p>
     *
     * <p>@Transactional 的意义非常关键：
     * 质检记录保存、批次状态更新、订单状态联动必须属于一个原子操作，
     * 任何一步失败都应该整体回滚，避免“记录写了但批次没更新”这类脏状态。</p>
     */
    public Batch inspectBatch(Long batchId, String result, String remarks, String inspectorEmail) {
        Batch batch = requireBatch(batchId);
        String normalizedResult = normalizeResult(result);
        User inspector = resolveInspector(inspectorEmail);

        // 已完成质检的批次不允许重复检验，
        // 防止同一批次被重复提交导致状态覆盖和审计混乱。
        if (!STATUS_PENDING.equals(safe(batch.getQualityStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该批次已完成质检，如需返工请由生产管理员重新完工后再检验");
        }

        // 质检不合格必须写明原因，否则生产侧无法根据结果组织返工。
        if (STATUS_FAILED.equals(normalizedResult) && isBlank(remarks)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不合格时请填写具体原因");
        }

        // 先构建一条独立的质检记录，作为质量审计留痕。
        // 企业系统里“结果留痕”很重要，不能只改批次状态而不存历史记录。
        QualityRecord record = new QualityRecord();
        record.setBatch(batch);
        record.setProduct(batch.getProduct());
        record.setInspector(inspector == null ? null : inspector.getId());
        record.setInspectorName(inspector == null ? safe(inspectorEmail) : inspector.getName());
        record.setInspectionDate(LocalDateTime.now());
        record.setResult(normalizedResult);
        record.setRemarks(blankToNull(remarks));

        // 下面把“本次质检结果”同步回批次主表，
        // 目的是让批次列表无需联表查询即可直接显示当前状态。
        batch.setQualityStatus(normalizedResult);
        batch.setQualityRemark(blankToNull(remarks));
        batch.setQualityInspectedAt(record.getInspectionDate());
        batch.setQualityInspectorId(record.getInspector());
        batch.setQualityInspectorName(record.getInspectorName());

        // 不合格时立即通知生产经理返工，形成质量闭环。
        if (STATUS_FAILED.equals(normalizedResult) && !isBlank(batch.getProductionManagerEmail())) {
            record.setNotificationSent(true);
            record.setNotifiedProductionManagerEmail(batch.getProductionManagerEmail());
            record.setNotifiedAt(LocalDateTime.now());
            notifyProductionManager(batch, record);
        }

        // 先保存质检记录，再保存批次当前状态，随后再联动订单状态。
        qualityRecordRepository.save(record);
        Batch saved = batchRepository.save(batch);
        updateRelatedOrderStatus(saved, normalizedResult, remarks, inspectorEmail);

        // 最后广播一条统一的质量事件，供前端质量页、生产页、仓库页实时刷新。
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
        // 针对不合格批次，定向推送给对应生产负责人，
        // 这是典型的“按责任人分发消息”的企业通知设计。
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
        // 批次质检通过后，仓库才有资格做成品入库，
        // 因此这里主动给仓库推送“待入库”事件。
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
        // 质检并不是孤立动作，它会直接影响来源订单的下一步流转。
        // 因此这里把 batch -> sales order 的业务联动封装在一个方法中统一处理。
        if (isBlank(batch.getSourceOrderNo())) {
            return;
        }
        SalesOrder order = salesOrderRepository.findAll().stream()
                .filter(item -> item.getOrderNo() != null && item.getOrderNo().equalsIgnoreCase(batch.getSourceOrderNo()))
                .findFirst()
                .orElse(null);
        if (order == null) {
            if (STATUS_PASSED.equals(normalizedResult) && isStandaloneProductionPlanBatch(batch)) {
                notifyWarehouseForQualifiedBatch(batch, remarks);
            }
            return;
        }

        // 质检不合格：订单退回生产中，等待返工。
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

        // 质检合格时，需要检查同一订单下的所有批次是否都已经合格。
        // 只有全部通过，订单才能整体进入“待生产入库”。
        List<Batch> orderBatches = batchRepository.findBySourceOrderNoIgnoreCaseOrderByCreatedAtDesc(order.getOrderNo());
        boolean allPassed = !orderBatches.isEmpty() && orderBatches.stream()
                .allMatch(item -> STATUS_PASSED.equals(safe(item.getQualityStatus())));
        if (allPassed) {
            order.setStatus(OrderWorkflowService.STATUS_PENDING_PRODUCTION_STOCK_IN);
            salesOrderRepository.save(order);

            // 订单已经具备入库条件，通知仓库与订单模块同步刷新。
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
        // 统一的批次存在性校验方法，避免多处重复写 findById + throw。
        if (batchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批次ID不能为空");
        }
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在: " + batchId));
    }

    private User resolveInspector(String inspectorEmail) {
        // 根据邮箱恢复当前质检员实体，是“认证身份 -> 领域用户”转换的常见模式。
        if (isBlank(inspectorEmail)) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(inspectorEmail.trim()).orElse(null);
    }

    private boolean matchesKeyword(Batch batch, String keyword) {
        // 批次搜索支持批次号、来源订单、质检状态、检验人、产品名等多个字段，
        // 提升追溯效率。
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

    private boolean isStandaloneProductionPlanBatch(Batch batch) {
        return batch != null
                && batch.getSourceOrderNo() != null
                && (batch.getSourceOrderNo().startsWith("PLAN-ALERT-")
                || batch.getSourceOrderNo().startsWith("PLAN-MANUAL-"));
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

