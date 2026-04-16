package com.code.service;

import com.code.entity.Batch;
import com.code.entity.Product;
import com.code.entity.QualityRecord;
import com.code.entity.User;
import com.code.repository.BatchRepository;
import com.code.repository.QualityRecordRepository;
import com.code.repository.UserRepository;
import com.code.websocket.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class QualityServiceTest {

    @InjectMocks
    private QualityService qualityService;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private QualityRecordRepository qualityRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Test
    void inspectBatchAsFailedShouldNotifyResponsibleProductionManager() {
        Batch batch = buildBatch();
        User inspector = new User();
        inspector.setId(9L);
        inspector.setEmail("quality@test.com");
        inspector.setFullName("质检员甲");

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(userRepository.findByEmailIgnoreCase("quality@test.com")).thenReturn(Optional.of(inspector));
        when(qualityRecordRepository.save(any(QualityRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Batch result = qualityService.inspectBatch(1L, "不合格", "表面划伤", "quality@test.com");

        assertEquals(QualityService.STATUS_FAILED, result.getQualityStatus());
        assertEquals("质检员甲", result.getQualityInspectorName());
        assertEquals("表面划伤", result.getQualityRemark());
        verify(notificationService).broadcast(eq("/topic/production/manager/prod-test-com"), any());
        verify(notificationService).broadcast(eq("/topic/quality"), any());

        ArgumentCaptor<QualityRecord> recordCaptor = ArgumentCaptor.forClass(QualityRecord.class);
        verify(qualityRecordRepository).save(recordCaptor.capture());
        assertTrue(Boolean.TRUE.equals(recordCaptor.getValue().getNotificationSent()));
        assertEquals("prod@test.com", recordCaptor.getValue().getNotifiedProductionManagerEmail());
    }

    @Test
    void inspectBatchAsPassedShouldOnlyPersistRecordWithoutProductionNotification() {
        Batch batch = buildBatch();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(userRepository.findByEmailIgnoreCase("quality@test.com")).thenReturn(Optional.empty());
        when(qualityRecordRepository.save(any(QualityRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Batch result = qualityService.inspectBatch(1L, "合格", "尺寸正常", "quality@test.com");

        assertEquals(QualityService.STATUS_PASSED, result.getQualityStatus());
        assertEquals("quality@test.com", result.getQualityInspectorName());
        verify(notificationService, never()).broadcast(eq("/topic/production/manager/prod-test-com"), any());
        verify(notificationService).broadcast(eq("/topic/quality"), any());

        ArgumentCaptor<QualityRecord> recordCaptor = ArgumentCaptor.forClass(QualityRecord.class);
        verify(qualityRecordRepository).save(recordCaptor.capture());
        assertFalse(Boolean.TRUE.equals(recordCaptor.getValue().getNotificationSent()));
    }

    @Test
    void listProductionAlertsReturnsOnlyFailedBatchesForCurrentManager() {
        Batch failed = buildBatch();
        failed.setQualityStatus(QualityService.STATUS_FAILED);
        failed.setQualityInspectedAt(LocalDateTime.now());
        when(batchRepository.findByProductionManagerEmailIgnoreCaseAndQualityStatusOrderByQualityInspectedAtDesc("prod@test.com", QualityService.STATUS_FAILED))
                .thenReturn(List.of(failed));

        List<Batch> result = qualityService.listProductionAlerts("prod@test.com");

        assertEquals(1, result.size());
        assertEquals(QualityService.STATUS_FAILED, result.get(0).getQualityStatus());
    }

    @Test
    void inspectBatchAsFailedWithoutReasonShouldBeRejected() {
        Batch batch = buildBatch();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> qualityService.inspectBatch(1L, "FAIL", "   ", "quality@test.com")
        );

        assertEquals("不合格时请填写具体原因", exception.getReason());
        verify(qualityRecordRepository, never()).save(any(QualityRecord.class));
    }

    private Batch buildBatch() {
        Product product = new Product();
        product.setId(100L);
        product.setSku("FG-100");
        product.setName("成品机柜");

        Batch batch = new Batch();
        batch.setId(1L);
        batch.setBatchNo("BT-TEST01");
        batch.setProduct(product);
        batch.setQuantity(8.0);
        batch.setSourceOrderNo("SO-100");
        batch.setProductionManagerEmail("prod@test.com");
        batch.setProductionManagerName("生产主管甲");
        batch.setQualityStatus(QualityService.STATUS_PENDING);
        return batch;
    }
}

