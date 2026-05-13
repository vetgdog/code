package com.code.controller;

import com.code.entity.Product;
import com.code.entity.ProductionPlan;
import com.code.entity.PurchaseRequest;
import com.code.entity.User;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.repository.UserRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAlertControllerTest {

    @InjectMocks
    private InventoryAlertController inventoryAlertController;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Test
    void createProductionPlanShouldBroadcastReadableChineseNotification() {
        Product product = buildProduct(11L, "FG-001", "成品A", "FINISHED_GOOD", 10.0, "");
        User operator = buildOperator();
        Authentication authentication = mock(Authentication.class);
        InventoryAlertController.AlertActionRequest request = new InventoryAlertController.AlertActionRequest();
        request.setQuantity(12.0);
        request.setNote("库存预警触发，优先补产");

        when(authentication.getName()).thenReturn("warehouse@test.com");
        when(productRepository.findById(11L)).thenReturn(Optional.of(product));
        when(userRepository.findByEmailIgnoreCase("warehouse@test.com")).thenReturn(Optional.of(operator));
        when(inventoryItemRepository.findByProductId(11L)).thenReturn(List.of());
        when(productionPlanRepository.findAll()).thenReturn(List.of());
        when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> {
            ProductionPlan plan = invocation.getArgument(0);
            plan.setId(99L);
            return plan;
        });
        doNothing().when(notificationService).broadcast(any(), any(NotificationMessage.class));

        ProductionPlan saved = inventoryAlertController.createProductionPlan(11L, request, authentication);

        assertEquals(99L, saved.getId());
        assertTrue(saved.getPlanNo().startsWith("PLAN-ALERT-11-"));
        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService, times(1)).broadcast(any(), messageCaptor.capture());
        NotificationMessage message = messageCaptor.getAllValues().get(0);
        assertEquals("INVENTORY_ALERT_PRODUCTION_PLAN_CREATED", message.getMessageType());
        Object payload = message.getPayload();
        assertInstanceOf(InventoryAlertController.AlertNotificationPayload.class, payload);
        InventoryAlertController.AlertNotificationPayload alertPayload = (InventoryAlertController.AlertNotificationPayload) payload;
        assertTrue(alertPayload.getNotificationTitle().contains("库存预警已生成补产计划"));
        assertTrue(alertPayload.getNotificationTitle().contains("成品A"));
        assertTrue(alertPayload.getNotificationMeta().contains(saved.getPlanNo()));
        assertTrue(alertPayload.getNotificationMeta().contains("12.00"));
        assertTrue(alertPayload.getNotificationMeta().contains("先提交原材料申请"));
    }

    @Test
    void createProductionPlanShouldRejectDuplicateOpenAlertPlan() {
        Product product = buildProduct(11L, "FG-001", "成品A", "FINISHED_GOOD", 10.0, "");
        User operator = buildOperator();
        Authentication authentication = mock(Authentication.class);
        ProductionPlan existing = new ProductionPlan();
        existing.setPlanNo("PLAN-ALERT-11-202605130001");
        existing.setProduct(product);
        existing.setStatus("PLANNED");

        when(authentication.getName()).thenReturn("warehouse@test.com");
        when(productRepository.findById(11L)).thenReturn(Optional.of(product));
        when(userRepository.findByEmailIgnoreCase("warehouse@test.com")).thenReturn(Optional.of(operator));
        when(inventoryItemRepository.findByProductId(11L)).thenReturn(List.of());
        when(productionPlanRepository.findAll()).thenReturn(List.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> inventoryAlertController.createProductionPlan(11L, new InventoryAlertController.AlertActionRequest(), authentication));

        assertEquals(409, ex.getStatus().value());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("未完成的库存预警生产计划"));
    }

    @Test
    void createPurchaseRequestShouldBroadcastReadableChineseNotification() {
        Product product = buildProduct(21L, "RM-001", "原材料A", "RAW_MATERIAL", 20.0, "华东钢材供应商");
        User operator = buildOperator();
        Authentication authentication = mock(Authentication.class);
        InventoryAlertController.AlertActionRequest request = new InventoryAlertController.AlertActionRequest();
        request.setQuantity(9.5);
        request.setNote("仓库建议尽快补料");

        when(authentication.getName()).thenReturn("warehouse@test.com");
        when(productRepository.findById(21L)).thenReturn(Optional.of(product));
        when(userRepository.findByEmailIgnoreCase("warehouse@test.com")).thenReturn(Optional.of(operator));
        when(inventoryItemRepository.findByProductId(21L)).thenReturn(List.of());
        when(purchaseOrderRepository.findAll()).thenReturn(List.of());
        when(purchaseRequestRepository.findByStatusOrderByRequestDateDesc("OPEN")).thenReturn(List.of());
        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(invocation -> {
            PurchaseRequest purchaseRequest = invocation.getArgument(0);
            purchaseRequest.setId(55L);
            return purchaseRequest;
        });
        doNothing().when(notificationService).broadcast(any(), any(NotificationMessage.class));

        PurchaseRequest saved = inventoryAlertController.createPurchaseRequest(21L, request, authentication);

        assertEquals(55L, saved.getId());
        assertTrue(saved.getRequestNo().startsWith("PR-ALERT-"));
        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService, times(1)).broadcast(any(), messageCaptor.capture());
        NotificationMessage message = messageCaptor.getAllValues().get(0);
        assertEquals("INVENTORY_ALERT_PURCHASE_REQUEST_CREATED", message.getMessageType());
        Object payload = message.getPayload();
        assertInstanceOf(InventoryAlertController.AlertNotificationPayload.class, payload);
        InventoryAlertController.AlertNotificationPayload alertPayload = (InventoryAlertController.AlertNotificationPayload) payload;
        assertTrue(alertPayload.getNotificationTitle().contains("库存预警已生成采购申请"));
        assertTrue(alertPayload.getNotificationTitle().contains("原材料A"));
        assertTrue(alertPayload.getNotificationMeta().contains(saved.getRequestNo()));
        assertTrue(alertPayload.getNotificationMeta().contains("9.50"));
        assertTrue(alertPayload.getNotificationMeta().contains("华东钢材供应商"));
        assertTrue(alertPayload.getNotificationMeta().contains("采购管理员"));
    }

    private Product buildProduct(Long id, String sku, String name, String productType, Double safetyStock, String preferredSupplier) {
        Product product = new Product();
        product.setId(id);
        product.setSku(sku);
        product.setName(name);
        product.setProductType(productType);
        product.setSafetyStock(safetyStock);
        product.setPreferredSupplier(preferredSupplier);
        return product;
    }

    private User buildOperator() {
        User user = new User();
        user.setId(7L);
        user.setEmail("warehouse@test.com");
        user.setFullName("仓库管理员甲");
        return user;
    }
}

