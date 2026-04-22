package com.code.service;

import com.code.entity.InventoryItem;
import com.code.entity.OrderItem;
import com.code.entity.Product;
import com.code.entity.ProductionMaterialRequest;
import com.code.entity.PurchaseRequest;
import com.code.entity.Role;
import com.code.entity.SalesOrder;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionMaterialRequestRepository;
import com.code.repository.PurchaseRequestRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionMaterialRequestServiceTest {

    @InjectMocks
    private ProductionMaterialRequestService productionMaterialRequestService;

    @Mock
    private ProductionMaterialRequestRepository requestRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StockTransactionRepository stockTransactionRepository;
    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @Test
    void createRequestShouldPersistPendingWarehouseReviewRequest() {
        SalesOrder order = buildProductionOrder(1L, 101L, "SO-001");
        User productionManager = user(10L, "prod@test.com", "生产管理员甲", "ROLE_PRODUCTION_MANAGER");
        Product raw = rawMaterial(201L, "RM-001", "冷轧钢板");

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(requestRepository.findBySalesOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(userRepository.findByEmailIgnoreCase("prod@test.com")).thenReturn(Optional.of(productionManager));
        when(productRepository.findById(201L)).thenReturn(Optional.of(raw));
        when(requestRepository.save(any(ProductionMaterialRequest.class))).thenAnswer(invocation -> {
            ProductionMaterialRequest saved = invocation.getArgument(0);
            saved.setId(88L);
            return saved;
        });

        ProductionMaterialRequest result = productionMaterialRequestService.createRequest(
                1L,
                List.of(new ProductionMaterialRequestService.MaterialItemCommand(201L, 12.0)),
                "优先备料",
                "prod@test.com"
        );

        assertEquals(ProductionMaterialRequestService.STATUS_PENDING_WAREHOUSE_REVIEW, result.getStatus());
        assertEquals(1, result.getItems().size());
        assertEquals("冷轧钢板", result.getItems().get(0).getMaterialProduct().getName());
        verify(notificationService).broadcast(eq("/topic/orders/warehouse"), any());
    }

    @Test
    void warehouseReviewShouldTriggerPurchaseRequestWhenShortageExists() {
        SalesOrder order = buildProductionOrder(2L, 102L, "SO-002");
        Product raw = rawMaterial(202L, "RM-002", "铝板");
        User warehouseManager = user(20L, "warehouse@test.com", "仓库管理员甲", "ROLE_WAREHOUSE_MANAGER");

        ProductionMaterialRequest request = new ProductionMaterialRequest();
        request.setId(90L);
        request.setRequestNo("PMR-0001");
        request.setSalesOrder(order);
        request.setStatus(ProductionMaterialRequestService.STATUS_PENDING_WAREHOUSE_REVIEW);
        request.setProcurementTriggered(false);
        request.setItems(List.of(item(request, raw, 10.0)));

        InventoryItem inventory = new InventoryItem();
        inventory.setQuantity(3.0);
        inventory.setReservedQuantity(0.0);

        when(requestRepository.findById(90L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmailIgnoreCase("warehouse@test.com")).thenReturn(Optional.of(warehouseManager));
        when(inventoryItemRepository.findByProductId(202L)).thenReturn(List.of(inventory));
        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(ProductionMaterialRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionMaterialRequest result = productionMaterialRequestService.warehouseReview(90L, "库存不足", "warehouse@test.com");

        assertEquals(ProductionMaterialRequestService.STATUS_WAITING_PROCUREMENT, result.getStatus());
        verify(purchaseRequestRepository).save(any(PurchaseRequest.class));
        verify(notificationService).broadcast(eq("/topic/procurement/manager"), any());
        verify(stockTransactionRepository, never()).save(any());
    }

    @Test
    void warehouseReviewShouldIssueMaterialsWhenStockEnough() {
        SalesOrder order = buildProductionOrder(3L, 103L, "SO-003");
        Product raw = rawMaterial(203L, "RM-003", "镀锌板");
        User warehouseManager = user(21L, "warehouse@test.com", "仓库管理员甲", "ROLE_WAREHOUSE_MANAGER");
        Warehouse warehouse = new Warehouse();
        warehouse.setId(9L);
        warehouse.setName("原料仓");

        ProductionMaterialRequest request = new ProductionMaterialRequest();
        request.setId(91L);
        request.setRequestNo("PMR-0002");
        request.setSalesOrder(order);
        request.setCreatedByEmail("prod@test.com");
        request.setStatus(ProductionMaterialRequestService.STATUS_PENDING_WAREHOUSE_REVIEW);
        request.setProcurementTriggered(false);
        request.setItems(List.of(item(request, raw, 6.0)));

        InventoryItem inventory = new InventoryItem();
        inventory.setId(100L);
        inventory.setProduct(raw);
        inventory.setWarehouse(warehouse);
        inventory.setQuantity(8.0);
        inventory.setReservedQuantity(1.0);

        when(requestRepository.findById(91L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmailIgnoreCase("warehouse@test.com")).thenReturn(Optional.of(warehouseManager));
        when(inventoryItemRepository.findByProductId(203L)).thenReturn(List.of(inventory));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(ProductionMaterialRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionMaterialRequest result = productionMaterialRequestService.warehouseReview(91L, "可出库", "warehouse@test.com");

        assertEquals(ProductionMaterialRequestService.STATUS_READY_FOR_PRODUCTION, result.getStatus());
        assertTrue(result.getItems().stream().allMatch(item -> item.getIssuedQuantity() != null && item.getIssuedQuantity() > 0));
        assertEquals(2.0, inventory.getQuantity());
        verify(stockTransactionRepository).save(any());
        verify(notificationService).broadcast(eq("/topic/production"), any());
    }

    @Test
    void createRequestShouldSupportMultipleRawMaterialTypes() {
        SalesOrder order = buildProductionOrder(4L, 104L, "SO-004");
        User productionManager = user(30L, "prod@test.com", "生产管理员乙", "ROLE_PRODUCTION_MANAGER");
        Product rawA = rawMaterial(204L, "RM-204", "冷轧钢板");
        Product rawB = rawMaterial(205L, "RM-205", "镀锌板");

        when(salesOrderRepository.findById(4L)).thenReturn(Optional.of(order));
        when(requestRepository.findBySalesOrderIdOrderByCreatedAtDesc(4L)).thenReturn(List.of());
        when(userRepository.findByEmailIgnoreCase("prod@test.com")).thenReturn(Optional.of(productionManager));
        when(productRepository.findById(204L)).thenReturn(Optional.of(rawA));
        when(productRepository.findById(205L)).thenReturn(Optional.of(rawB));
        when(requestRepository.save(any(ProductionMaterialRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionMaterialRequest result = productionMaterialRequestService.createRequest(
                4L,
                List.of(
                        new ProductionMaterialRequestService.MaterialItemCommand(204L, 6.0),
                        new ProductionMaterialRequestService.MaterialItemCommand(205L, 2.5)
                ),
                "组合领料",
                "prod@test.com"
        );

        assertEquals(2, result.getItems().size());
        assertTrue(result.getItems().stream().anyMatch(item -> "冷轧钢板".equals(item.getMaterialProduct().getName())));
        assertTrue(result.getItems().stream().anyMatch(item -> "镀锌板".equals(item.getMaterialProduct().getName())));
    }

    private SalesOrder buildProductionOrder(Long id, Long productId, String orderNo) {
        Product finished = new Product();
        finished.setId(productId);
        finished.setSku("FG-" + productId);
        finished.setName("成品-" + productId);
        finished.setProductType("FINISHED_GOOD");

        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(finished);
        orderItem.setQuantity(5.0);

        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus(OrderWorkflowService.STATUS_IN_PRODUCTION);
        order.setItems(List.of(orderItem));
        return order;
    }

    private Product rawMaterial(Long id, String sku, String name) {
        Product product = new Product();
        product.setId(id);
        product.setSku(sku);
        product.setName(name);
        product.setProductType("RAW_MATERIAL");
        return product;
    }

    private User user(Long id, String email, String fullName, String roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setUsername(fullName);
        user.setRoles(java.util.Set.of(role));
        return user;
    }

    private com.code.entity.ProductionMaterialRequestItem item(ProductionMaterialRequest request, Product raw, Double quantity) {
        com.code.entity.ProductionMaterialRequestItem item = new com.code.entity.ProductionMaterialRequestItem();
        item.setRequest(request);
        item.setMaterialProduct(raw);
        item.setRequiredQuantity(quantity);
        item.setIssuedQuantity(0.0);
        return item;
    }
}

