package com.code.controller;

import com.code.entity.Batch;
import com.code.entity.Product;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.ProductionTaskRepository;
import com.code.service.ProductionMaterialRequestService;
import com.code.service.QualityService;
import com.code.service.WeeklyPlanningService;
import com.code.websocket.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionControllerTest {

    @InjectMocks
    private ProductionController productionController;

    @Mock
    private ProductionTaskRepository productionTaskRepository;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private QualityService qualityService;

    @Mock
    private WeeklyPlanningService weeklyPlanningService;

    @Mock
    private ProductionMaterialRequestService productionMaterialRequestService;

    @Test
    void listQualityAlertsShouldReturnMappedFailedBatchesForCurrentProductionManager() {
        Product product = new Product();
        product.setId(10L);
        product.setSku("FG-100");
        product.setName("成品机柜");

        Batch batch = new Batch();
        batch.setId(9L);
        batch.setBatchNo("BT-ALERT-001");
        batch.setSourceOrderNo("SO-20260417-01");
        batch.setProduct(product);
        batch.setQuantity(12.0);
        batch.setQualityStatus(QualityService.STATUS_FAILED);
        batch.setQualityRemark("尺寸偏差超标");
        batch.setQualityInspectorName("质检员甲");
        batch.setQualityInspectedAt(LocalDateTime.of(2026, 4, 17, 10, 30));

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("prod@test.com");
        when(qualityService.listProductionAlerts("prod@test.com")).thenReturn(List.of(batch));

        List<ProductionController.ProductionQualityAlertView> result = productionController.listQualityAlerts(authentication);

        assertEquals(1, result.size());
        assertEquals("BT-ALERT-001", result.get(0).getBatchNo());
        assertEquals("SO-20260417-01", result.get(0).getOrderNo());
        assertEquals("FG-100", result.get(0).getProductSku());
        assertEquals("成品机柜", result.get(0).getProductName());
        assertEquals(12.0, result.get(0).getQuantity());
        assertEquals(QualityService.STATUS_FAILED, result.get(0).getQualityStatus());
        assertEquals("尺寸偏差超标", result.get(0).getQualityRemark());
        assertEquals("质检员甲", result.get(0).getInspectorName());
        verify(qualityService).listProductionAlerts("prod@test.com");
    }
}


