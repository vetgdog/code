package com.code.service;

import com.code.entity.InventoryItem;
import com.code.entity.OrderItem;
import com.code.entity.Product;
import com.code.entity.SalesOrder;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesOrderRepository;
import com.code.websocket.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowServiceTest {

    @InjectMocks
    private OrderWorkflowService orderWorkflowService;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private NotificationService notificationService;

    @Test
    void warehouseReviewWhenStockEnoughReservesInventoryAndMarksAccepted() {
        SalesOrder order = buildOrder(1L, OrderWorkflowService.STATUS_PENDING_WAREHOUSE_CHECK, 100L, 5.0);
        InventoryItem inventory = new InventoryItem();
        inventory.setId(10L);
        inventory.setQuantity(10.0);
        inventory.setReservedQuantity(2.0);

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByProductId(100L)).thenReturn(List.of(inventory));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderWorkflowService.WarehouseReviewResult result = orderWorkflowService.warehouseReview(1L, "warehouse@test.com", "ok");

        assertEquals(OrderWorkflowService.STATUS_ACCEPTED, result.getOrder().getStatus());
        assertTrue(result.getShortages().isEmpty());
        ArgumentCaptor<List<InventoryItem>> inventoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(inventoryItemRepository).saveAll(inventoryCaptor.capture());
        assertEquals(7.0, inventoryCaptor.getValue().get(0).getReservedQuantity());
        verify(productionPlanRepository, never()).save(any());
    }

    @Test
    void warehouseReviewWhenStockInsufficientCreatesProductionPlan() {
        SalesOrder order = buildOrder(2L, OrderWorkflowService.STATUS_PENDING_WAREHOUSE_CHECK, 200L, 8.0);
        InventoryItem inventory = new InventoryItem();
        inventory.setId(20L);
        inventory.setQuantity(3.0);
        inventory.setReservedQuantity(0.0);

        when(salesOrderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByProductId(200L)).thenReturn(List.of(inventory));
        when(productRepository.findById(200L)).thenReturn(Optional.of(order.getItems().get(0).getProduct()));
        when(productionPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderWorkflowService.WarehouseReviewResult result = orderWorkflowService.warehouseReview(2L, "warehouse@test.com", "shortage");

        assertEquals(OrderWorkflowService.STATUS_IN_PRODUCTION, result.getOrder().getStatus());
        assertEquals(1, result.getShortages().size());
        assertEquals(5.0, result.getShortages().get(0).getShortageQuantity());
        assertEquals(1, result.getProductionPlans().size());
        verify(productionPlanRepository).save(any());
    }

    private SalesOrder buildOrder(Long id, String status, Long productId, Double qty) {
        Product product = new Product();
        product.setId(productId);
        product.setName("P-" + productId);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(qty);

        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo("SO-" + id);
        order.setStatus(status);
        order.setItems(List.of(item));
        return order;
    }
}

