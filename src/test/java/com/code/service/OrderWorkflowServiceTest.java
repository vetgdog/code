package com.code.service;

import com.code.entity.Customer;
import com.code.entity.InventoryItem;
import com.code.entity.OrderItem;
import com.code.entity.ProductionPlan;
import com.code.entity.Product;
import com.code.entity.SalesOrder;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.WarehouseRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowServiceTest {

    private static final String CUSTOMER_TOPIC = "/topic/orders/customer/customer-example-com";

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
    private StockTransactionRepository stockTransactionRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

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
        verify(notificationService).broadcast(eq("/topic/orders/sales"), any());
        verify(notificationService, never()).broadcast(eq("/topic/orders/warehouse"), any());
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
        verify(notificationService).broadcast(eq("/topic/orders/production"), any());
        verify(notificationService, never()).broadcast(eq("/topic/orders/warehouse"), any());
    }

    @Test
    void markProductionCompletedAutoStocksInAndNotifiesWarehouse() {
        SalesOrder order = buildOrder(3L, OrderWorkflowService.STATUS_IN_PRODUCTION, 300L, 4.0);
        ProductionPlan plan = new ProductionPlan();
        plan.setId(1L);
        plan.setPlanNo("PLAN-" + order.getOrderNo() + "-300");
        plan.setProduct(order.getItems().get(0).getProduct());
        plan.setPlannedQuantity(4.0);
        plan.setCreatedBy(99L);
        plan.setStatus("PLANNED");
        Warehouse warehouse = new Warehouse();
        warehouse.setId(8L);
        warehouse.setName("成品仓");

        when(inventoryItemRepository.findByProductId(300L)).thenReturn(List.of(), List.of());
        when(warehouseRepository.findAll()).thenReturn(List.of(warehouse));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(salesOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productionPlanRepository.findByPlanNoStartingWith("PLAN-" + order.getOrderNo() + "-")).thenReturn(List.of(plan));
        when(productionPlanRepository.findByPlanNoStartingWithAndStatus("PLAN-" + order.getOrderNo() + "-", "DONE")).thenReturn(List.of(plan));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrder result = orderWorkflowService.markProductionCompleted(3L, "prod@test.com", "done");

        assertEquals(OrderWorkflowService.STATUS_PENDING_WAREHOUSE_CHECK, result.getStatus());
        assertEquals("WAREHOUSED", plan.getStatus());
        verify(productionPlanRepository, atLeastOnce()).saveAll(any());
        ArgumentCaptor<InventoryItem> inventoryCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).save(inventoryCaptor.capture());
        assertEquals(4.0, inventoryCaptor.getValue().getQuantity());
        assertNotNull(inventoryCaptor.getValue().getWarehouse());
        verify(notificationService).broadcast(eq("/topic/orders/warehouse"), any());
    }

    @Test
    void markOrderShippedConsumesReservedInventoryAndNotifiesCustomer() {
        SalesOrder order = buildOrder(4L, OrderWorkflowService.STATUS_ACCEPTED, 400L, 3.0);
        Customer customer = new Customer();
        customer.setEmail("customer@example.com");
        order.setCustomer(customer);
        InventoryItem inventory = new InventoryItem();
        inventory.setId(40L);
        inventory.setQuantity(10.0);
        inventory.setReservedQuantity(5.0);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(2L);
        inventory.setWarehouse(warehouse);

        when(salesOrderRepository.findById(4L)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByProductId(400L)).thenReturn(List.of(inventory));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrder result = orderWorkflowService.markOrderShipped(4L, "warehouse@test.com", "ship");

        assertEquals(OrderWorkflowService.STATUS_SHIPPED, result.getStatus());
        assertEquals(7.0, inventory.getQuantity());
        assertEquals(2.0, inventory.getReservedQuantity());
        verify(notificationService).broadcast(eq(CUSTOMER_TOPIC), any());
        verify(notificationService).broadcast(eq("/topic/orders/sales"), any());
        verify(notificationService, never()).broadcast(eq("/topic/orders/warehouse"), any());
    }

    @Test
    void routeToWarehouseCheckOnlyNotifiesWarehouseTopic() {
        SalesOrder order = buildOrder(5L, OrderWorkflowService.STATUS_PENDING_SALES_REVIEW, 500L, 2.0);

        when(salesOrderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrder result = orderWorkflowService.routeToWarehouseCheck(5L, "sales@test.com");

        assertEquals(OrderWorkflowService.STATUS_PENDING_WAREHOUSE_CHECK, result.getStatus());
        verify(notificationService).broadcast(eq("/topic/orders/warehouse"), any());
        verify(notificationService, never()).broadcast(eq("/topic/orders/sales"), any());
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

