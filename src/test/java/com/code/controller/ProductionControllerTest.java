package com.code.controller;

import com.code.entity.Batch;
import com.code.entity.Product;
import com.code.entity.ProductionMaterialRequest;
import com.code.entity.ProductionPlan;
import com.code.entity.User;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.ProductionTaskRepository;
import com.code.repository.UserRepository;
import com.code.service.OrderWorkflowService;
import com.code.service.ProductionMaterialRequestService;
import com.code.service.QualityService;
import com.code.service.WeeklyPlanningService;
import com.code.websocket.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private QualityService qualityService;

    @Mock
    private WeeklyPlanningService weeklyPlanningService;

    @Mock
    private ProductionMaterialRequestService productionMaterialRequestService;

    @Mock
    private OrderWorkflowService orderWorkflowService;

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

    @Test
    void listActivePlansShouldExposeInventoryAlertPlansAsPendingWork() {
        Product product = new Product();
        product.setId(7L);
        product.setSku("FG-ALERT-001");
        product.setName("预警补产成品");

        ProductionPlan alertPlan = new ProductionPlan();
        alertPlan.setId(15L);
        alertPlan.setPlanNo("PLAN-ALERT-7-202605130001");
        alertPlan.setProduct(product);
        alertPlan.setPlannedQuantity(18.0);
        alertPlan.setStatus("PLANNED");
        alertPlan.setCreatedBy(3L);
        alertPlan.setCreatedByName("仓库管理员甲");
        alertPlan.setCreatedAt(LocalDateTime.of(2026, 5, 13, 9, 30));

        when(productionPlanRepository.findAll()).thenReturn(List.of(alertPlan));

        List<ProductionController.ActiveProductionPlanView> result = productionController.listActivePlans();

        assertEquals(1, result.size());
        assertEquals("PLAN-ALERT-7-202605130001", result.get(0).getPlanNo());
        assertEquals("库存预警补产", result.get(0).getSourceType());
        assertEquals("", result.get(0).getOrderNo());
        assertEquals("FG-ALERT-001", result.get(0).getProductSku());
        assertEquals("预警补产成品", result.get(0).getProductName());
        assertEquals(18.0, result.get(0).getPlannedQuantity());
        assertEquals("PLANNED", result.get(0).getStatus());
    }

    @Test
    void listActivePlansShouldExposeManualPlansAsStandaloneWork() {
        Product product = new Product();
        product.setId(8L);
        product.setSku("FG-MANUAL-001");
        product.setName("手动生产成品");

        ProductionPlan manualPlan = new ProductionPlan();
        manualPlan.setId(16L);
        manualPlan.setPlanNo("PLAN-MANUAL-8-202605150001");
        manualPlan.setProduct(product);
        manualPlan.setPlannedQuantity(12.0);
        manualPlan.setStatus("PLANNED");
        manualPlan.setCreatedBy(5L);
        manualPlan.setCreatedByName("生产管理员甲");
        manualPlan.setCreatedAt(LocalDateTime.of(2026, 5, 15, 9, 30));

        when(productionPlanRepository.findAll()).thenReturn(List.of(manualPlan));

        List<ProductionController.ActiveProductionPlanView> result = productionController.listActivePlans();

        assertEquals(1, result.size());
        assertEquals("PLAN-MANUAL-8-202605150001", result.get(0).getPlanNo());
        assertEquals("手动创建生产单", result.get(0).getSourceType());
        assertEquals("", result.get(0).getOrderNo());
        assertEquals("FG-MANUAL-001", result.get(0).getProductSku());
        assertEquals("手动生产成品", result.get(0).getProductName());
    }

    @Test
    void createManualPlanShouldPersistStandaloneProductionPlan() {
        Product product = new Product();
        product.setId(28L);
        product.setSku("FG-028");
        product.setName("手动计划成品");
        product.setProductType("FINISHED_GOOD");

        User operator = new User();
        operator.setId(12L);
        operator.setEmail("prod@test.com");
        operator.setFullName("生产管理员甲");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("prod@test.com");
        when(productRepository.findById(28L)).thenReturn(Optional.of(product));
        when(userRepository.findByEmailIgnoreCase("prod@test.com")).thenReturn(Optional.of(operator));
        when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionController.ManualProductionPlanCommand command = new ProductionController.ManualProductionPlanCommand();
        command.setProductId(28L);
        command.setPlannedQuantity(14.0);

        ProductionPlan result = productionController.createManualPlan(command, authentication);

        assertNotNull(result);
        assertEquals("PLANNED", result.getStatus());
        assertEquals(product, result.getProduct());
        assertEquals(14.0, result.getPlannedQuantity());
        verify(notificationService).broadcast(eq("/topic/production"), any());
    }

    @Test
    void completeInventoryAlertPlanShouldDelegateToWorkflowService() {
        ProductionPlan plan = new ProductionPlan();
        plan.setId(19L);
        plan.setPlanNo("PLAN-ALERT-19-202605130001");
        plan.setStatus("DONE");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("prod@test.com");
        when(orderWorkflowService.completeStandaloneProductionPlan(19L, "prod@test.com", "补产完工")).thenReturn(plan);

        ProductionController.PlanActionCommand command = new ProductionController.PlanActionCommand();
        command.setNote("补产完工");
        ProductionPlan result = productionController.completeInventoryAlertPlan(19L, command, authentication);

        assertEquals("PLAN-ALERT-19-202605130001", result.getPlanNo());
        assertEquals("DONE", result.getStatus());
        verify(orderWorkflowService).completeStandaloneProductionPlan(19L, "prod@test.com", "补产完工");
    }

    @Test
    void createMaterialRequestShouldPassPlanIdToService() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("prod@test.com");

        ProductionMaterialRequest request = new ProductionMaterialRequest();
        request.setId(51L);
        request.setRequestNo("PMR-PLAN-001");
        when(productionMaterialRequestService.createRequest(any(), any(), any(), any(), any())).thenReturn(request);

        ProductionController.MaterialRequestCommand command = new ProductionController.MaterialRequestCommand();
        command.setPlanId(88L);
        command.setNote("库存预警补产先领料");

        ProductionController.MaterialItemCommand item = new ProductionController.MaterialItemCommand();
        item.setMaterialProductId(9L);
        item.setRequiredQuantity(4.5);
        command.setItems(List.of(item));

        ProductionMaterialRequest result = productionController.createMaterialRequest(command, authentication);

        assertNotNull(result);
        assertEquals("PMR-PLAN-001", result.getRequestNo());
        verify(productionMaterialRequestService).createRequest(isNull(), eq(88L), any(), eq("库存预警补产先领料"), eq("prod@test.com"));
    }

    @Test
    void listRecordOverviewShouldAllowAdminReadOnlyAccess() throws NoSuchMethodException {
        PreAuthorize annotation = ProductionController.class
                .getMethod("listRecordOverview", String.class, String.class, String.class, Authentication.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(annotation);
        assertEquals("hasAnyRole('PRODUCTION_MANAGER','ADMIN')", annotation.value());
    }
}


