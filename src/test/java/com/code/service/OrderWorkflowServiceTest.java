package com.code.service;

import com.code.entity.Batch;
import com.code.entity.Customer;
import com.code.entity.InventoryItem;
import com.code.entity.OrderItem;
import com.code.entity.ProductionPlan;
import com.code.entity.Product;
import com.code.entity.SalesOrder;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.BatchRepository;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private BatchRepository batchRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private UserRepository userRepository;

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
    void markProductionCompletedOnlyNotifiesWarehouse() {
        SalesOrder order = buildOrder(3L, OrderWorkflowService.STATUS_IN_PRODUCTION, 300L, 4.0);
        ProductionPlan plan = new ProductionPlan();
        plan.setId(1L);
        plan.setPlanNo("PLAN-" + order.getOrderNo() + "-300");
        plan.setProduct(order.getItems().get(0).getProduct());
        plan.setPlannedQuantity(4.0);
        plan.setCreatedBy(99L);
        plan.setStatus("PLANNED");

        User productionManager = new User();
        productionManager.setId(99L);
        productionManager.setEmail("prod@test.com");
        productionManager.setFullName("生产主管甲");

        when(salesOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productionPlanRepository.findByPlanNoStartingWith("PLAN-" + order.getOrderNo() + "-")).thenReturn(List.of(plan));
        when(userRepository.findByEmailIgnoreCase("prod@test.com")).thenReturn(Optional.of(productionManager));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrder result = orderWorkflowService.markProductionCompleted(3L, "prod@test.com", "done");

        assertEquals(OrderWorkflowService.STATUS_PENDING_WAREHOUSE_CHECK, result.getStatus());
        assertEquals("DONE", plan.getStatus());
        assertEquals("prod@test.com", plan.getCompletedByEmail());
        assertEquals("生产主管甲", plan.getCompletedByName());
        verify(productionPlanRepository).saveAll(any());
        verify(inventoryItemRepository, never()).save(any(InventoryItem.class));
        verify(notificationService).broadcast(eq("/topic/orders/warehouse"), any());
    }

    @Test
    void confirmProductionStockInReservesInventoryAndMarksAccepted() {
        SalesOrder order = buildOrder(6L, OrderWorkflowService.STATUS_PENDING_WAREHOUSE_CHECK, 600L, 4.0);
        ProductionPlan plan = new ProductionPlan();
        plan.setId(11L);
        plan.setPlanNo("PLAN-" + order.getOrderNo() + "-600");
        plan.setProduct(order.getItems().get(0).getProduct());
        plan.setPlannedQuantity(4.0);
        plan.setStatus("DONE");
        plan.setCompletedByEmail("prod@test.com");
        plan.setCompletedByName("生产主管甲");

        Warehouse warehouse = new Warehouse();
        warehouse.setId(8L);
        warehouse.setName("成品仓");

        java.util.List<InventoryItem> inventoryState = new java.util.ArrayList<>();
        java.util.List<Batch> batchState = new java.util.ArrayList<>();

        when(salesOrderRepository.findById(6L)).thenReturn(Optional.of(order));
        when(productionPlanRepository.findByPlanNoStartingWithAndStatus("PLAN-" + order.getOrderNo() + "-", "DONE")).thenReturn(List.of(plan));
        when(inventoryItemRepository.findByProductId(600L)).thenAnswer(invocation -> inventoryState);
        when(warehouseRepository.findAll()).thenReturn(List.of(warehouse));
        when(batchRepository.findBySourceOrderNoAndProductId(order.getOrderNo(), 600L)).thenReturn(Optional.empty());
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> {
            Batch saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(21L);
            }
            batchState.clear();
            batchState.add(saved);
            return saved;
        });
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem saved = invocation.getArgument(0);
            inventoryState.clear();
            inventoryState.add(saved);
            return saved;
        });
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrder result = orderWorkflowService.confirmProductionStockIn(6L, "warehouse@test.com", "stock in");

        assertEquals(OrderWorkflowService.STATUS_ACCEPTED, result.getStatus());
        assertEquals("WAREHOUSED", plan.getStatus());
        assertFalse(batchState.isEmpty());
        assertEquals(QualityService.STATUS_PENDING, batchState.get(0).getQualityStatus());
        assertEquals("prod@test.com", batchState.get(0).getProductionManagerEmail());
        assertEquals(order.getOrderNo(), batchState.get(0).getSourceOrderNo());
        verify(inventoryItemRepository).save(any(InventoryItem.class));
        verify(notificationService).broadcast(eq("/topic/orders/sales"), any());
        verify(notificationService).broadcast(eq("/topic/quality"), any());
        verify(batchRepository).save(any(Batch.class));
        verify(stockTransactionRepository).save(any());
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

