package com.code.service;

import com.code.dto.SupplierDashboardDto;
import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseOrderItem;
import com.code.entity.PurchaseRequest;
import com.code.entity.Role;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.UserRepository;
import com.code.repository.WarehouseRepository;
import com.code.support.WarehouseDefaults;
import com.code.websocket.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementWorkflowServiceTest {

    @InjectMocks
    private ProcurementWorkflowService procurementWorkflowService;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private NotificationService notificationService;

    @Test
    void createPurchaseOrderSetsWaitingSupplierAndNotifiesSupplier() {
        User supplier = supplierUser(1L, "supplier01", "供应商A", "supplier@example.com");

        Product material = new Product();
        material.setId(10L);
        material.setName("冷轧钢板");
        material.setSku("RM-001");
        material.setProductType("RAW_MATERIAL");
        material.setUnitPrice(5.5);
        material.setPreferredSupplier("供应商A");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProduct(material);
        item.setQuantity(8.0);
        item.setUnitPrice(6.0);

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setItems(List.of(item));

        when(userRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepository.findById(10L)).thenReturn(Optional.of(material));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = procurementWorkflowService.createPurchaseOrder(order, "proc@test.com");

        assertEquals(ProcurementWorkflowService.STATUS_WAITING_SUPPLIER, result.getStatus());
        assertEquals(48.0, result.getTotalAmount());
        org.junit.jupiter.api.Assertions.assertTrue(result.getPoNo().startsWith("PO"));
        verify(notificationService).broadcast(eq("/topic/procurement/supplier/supplier-example-com"), any());
        verify(notificationService, never()).broadcast(eq("/topic/procurement"), any());
    }

    @Test
    void createPurchaseOrderMarksSourceRequestsConverted() {
        User supplier = supplierUser(11L, "supplier11", "供应商甲", "supplier11@example.com");

        Product material = new Product();
        material.setId(110L);
        material.setName("冷轧钢卷");
        material.setSku("RM-110");
        material.setProductType("RAW_MATERIAL");
        material.setUnitPrice(4.2);
        material.setPreferredSupplier("供应商甲");

        PurchaseRequest purchaseRequest = new PurchaseRequest();
        purchaseRequest.setId(801L);
        purchaseRequest.setRequestNo("PR-MAT-801");
        purchaseRequest.setStatus("OPEN");
        purchaseRequest.setProduct(material);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProduct(material);
        item.setQuantity(5.0);
        item.setUnitPrice(4.5);

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setItems(List.of(item));
        order.setSourceRequestIds(List.of(801L));

        when(userRepository.findById(11L)).thenReturn(Optional.of(supplier));
        when(productRepository.findById(110L)).thenReturn(Optional.of(material));
        when(purchaseRequestRepository.findAllById(List.of(801L))).thenReturn(List.of(purchaseRequest));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = procurementWorkflowService.createPurchaseOrder(order, "proc@test.com");

        assertEquals(ProcurementWorkflowService.STATUS_WAITING_SUPPLIER, result.getStatus());
        assertEquals("CONVERTED", purchaseRequest.getStatus());
        assertEquals(supplier, purchaseRequest.getSupplier());
        verify(purchaseRequestRepository).saveAll(any());
    }

    @Test
    void supplierShipUpdatesStatusAndNotifiesProcurement() {
        User supplier = supplierUser(2L, "supplier02", "供应商乙", "supplier@example.com");

        PurchaseOrder order = new PurchaseOrder();
        order.setId(2L);
        order.setPoNo("PO-002");
        order.setSupplier(supplier);
        order.setStatus(ProcurementWorkflowService.STATUS_SUPPLIER_ACCEPTED);

        when(purchaseOrderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = procurementWorkflowService.supplierShip(2L, "supplier@test.com", "ready");

        assertEquals(ProcurementWorkflowService.STATUS_SUPPLIER_SHIPPED, result.getStatus());
        verify(notificationService).broadcast(eq("/topic/procurement/manager"), any());
    }

    @Test
    void warehouseReceiveStocksInItemsAndMarksWarehoused() {
        User supplier = supplierUser(3L, "supplier03", "供应商丙", "supplier@example.com");

        Product material = new Product();
        material.setId(30L);
        material.setName("铝板");
        material.setSku("RM-030");
        material.setProductType("RAW_MATERIAL");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProduct(material);
        item.setQuantity(15.0);
        item.setUnitPrice(7.2);

        PurchaseOrder order = new PurchaseOrder();
        order.setId(3L);
        order.setPoNo("PO-003");
        order.setSupplier(supplier);
        order.setStatus(ProcurementWorkflowService.STATUS_WAITING_WAREHOUSE_RECEIPT);
        order.setItems(List.of(item));

        Warehouse rawWarehouse = new Warehouse();
        rawWarehouse.setId(9L);
        rawWarehouse.setCode(WarehouseDefaults.RAW_MATERIAL_WAREHOUSE_CODE);
        rawWarehouse.setName("原料仓");

        Warehouse finishedWarehouse = new Warehouse();
        finishedWarehouse.setId(10L);
        finishedWarehouse.setCode(WarehouseDefaults.FINISHED_GOODS_WAREHOUSE_CODE);
        finishedWarehouse.setName("成品仓");

        when(purchaseOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByProductId(30L)).thenReturn(List.of());
        when(productRepository.findById(30L)).thenReturn(Optional.of(material));
        when(warehouseRepository.findByCodeIgnoreCase(WarehouseDefaults.RAW_MATERIAL_WAREHOUSE_CODE)).thenReturn(Optional.of(rawWarehouse));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = procurementWorkflowService.warehouseReceive(3L, "warehouse@test.com", "received");

        assertEquals(ProcurementWorkflowService.STATUS_WAREHOUSED, result.getStatus());
        org.mockito.ArgumentCaptor<InventoryItem> inventoryCaptor = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).save(inventoryCaptor.capture());
        assertSame(rawWarehouse, inventoryCaptor.getValue().getWarehouse());
        verify(notificationService).broadcast(eq("/topic/procurement/manager"), any());
    }

    @Test
    void buildSupplierDashboardReturnsSupplierSpecificTodosAndMaterials() {
        User supplier = supplierUser(5L, "SUP-A", "供应商甲", "supplier@example.com");

        Product preferredMaterial = new Product();
        preferredMaterial.setId(50L);
        preferredMaterial.setSku("RM-050");
        preferredMaterial.setName("镀锌板");
        preferredMaterial.setProductType("RAW_MATERIAL");
        preferredMaterial.setPreferredSupplier("供应商甲");
        preferredMaterial.setSafetyStock(20.0);

        PurchaseOrder waiting = new PurchaseOrder();
        waiting.setId(51L);
        waiting.setPoNo("PO-051");
        waiting.setStatus(ProcurementWorkflowService.STATUS_WAITING_SUPPLIER);
        waiting.setSupplier(supplier);
        PurchaseOrderItem waitingItem = new PurchaseOrderItem();
        waitingItem.setProduct(preferredMaterial);
        waitingItem.setQuantity(6.0);
        waiting.setItems(List.of(waitingItem));

        PurchaseOrder accepted = new PurchaseOrder();
        accepted.setId(52L);
        accepted.setPoNo("PO-052");
        accepted.setStatus(ProcurementWorkflowService.STATUS_SUPPLIER_ACCEPTED);
        accepted.setSupplier(supplier);
        PurchaseOrderItem acceptedItem = new PurchaseOrderItem();
        acceptedItem.setProduct(preferredMaterial);
        acceptedItem.setQuantity(3.0);
        accepted.setItems(List.of(acceptedItem));

        when(userRepository.findByEmailIgnoreCase("supplier@example.com")).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.findBySupplierIdOrderByOrderDateDesc(5L)).thenReturn(List.of(waiting, accepted));
        when(productRepository.findAll()).thenReturn(List.of(preferredMaterial));

        SupplierDashboardDto result = procurementWorkflowService.buildSupplierDashboard("supplier@example.com");

        assertEquals("供应商甲", result.getSupplierName());
        assertEquals(1, result.getPendingConfirmCount());
        assertEquals(1, result.getAcceptedPendingShipCount());
        assertEquals(2, result.getTotalOpenOrders());
        assertFalse(result.getRecommendedMaterials().isEmpty());
        assertFalse(result.getTodoOrders().isEmpty());
    }

    @Test
    void createPurchaseOrderRejectsSupplierMismatchWithMaterialArchive() {
        User supplier = supplierUser(6L, "SUP-A", "供应商A", "supplier-a@example.com");

        Product material = new Product();
        material.setId(60L);
        material.setName("不锈钢卷");
        material.setSku("RM-060");
        material.setProductType("RAW_MATERIAL");
        material.setPreferredSupplier("供应商B");

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProduct(material);
        item.setQuantity(3.0);
        item.setUnitPrice(10.0);

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setItems(List.of(item));

        when(userRepository.findById(6L)).thenReturn(Optional.of(supplier));
        when(productRepository.findById(60L)).thenReturn(Optional.of(material));

        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> procurementWorkflowService.createPurchaseOrder(order, "proc@test.com")
        );

        assertEquals(400, exception.getStatus().value());
        assertEquals("原材料 不锈钢卷 与所选供应商不匹配", exception.getReason());
    }

    @Test
    void resolveSupplierAccountReturnsSupplierUserAccount() {
        User user = supplierUser(7L, "legacy-supplier", "历史供应商", "legacy-supplier@example.com");

        when(userRepository.findByEmailIgnoreCase("legacy-supplier@example.com")).thenReturn(Optional.of(user));

        User result = procurementWorkflowService.resolveSupplierAccount("legacy-supplier@example.com");

        assertEquals("legacy-supplier@example.com", result.getEmail());
        assertEquals("历史供应商", result.getName());
    }

    private User supplierUser(Long id, String username, String fullName, String email) {
        Role supplierRole = new Role();
        supplierRole.setName("ROLE_SUPPLIER");

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRoles(java.util.Set.of(supplierRole));
        return user;
    }
}

