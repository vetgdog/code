package com.code.controller;

import com.code.entity.Batch;
import com.code.service.QualityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityControllerTest {

    @InjectMocks
    private QualityController qualityController;

    @Mock
    private QualityService qualityService;

    @Test
    void inspectShouldPassAuthenticatedInspectorEmailToService() {
        QualityController.InspectRequest request = new QualityController.InspectRequest();
        request.setResult("不合格");
        request.setRemarks("外观划伤");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("quality@test.com");

        Batch batch = new Batch();
        batch.setId(1L);
        batch.setBatchNo("BT-QUALITY-001");
        batch.setQualityStatus(QualityService.STATUS_FAILED);

        when(qualityService.inspectBatch(1L, "不合格", "外观划伤", "quality@test.com")).thenReturn(batch);

        ResponseEntity<Batch> response = qualityController.inspect(1L, request, authentication);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(batch, response.getBody());
        verify(qualityService).inspectBatch(1L, "不合格", "外观划伤", "quality@test.com");
    }
}

