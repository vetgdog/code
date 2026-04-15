package com.code.service;

import com.code.dto.SupplierDashboardDto;
import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseOrderItem;
import com.code.entity.Supplier;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.SupplierRepository;
import com.code.repository.WarehouseRepository;
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
    private SupplierRepository supplierRepository;

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
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setEmail("supplier@example.com");
        supplier.setName("供应商A");

        Product material = new Product();
        material.setId(10L);
        material.setName("冷轧钢板");
        material.setSku("RM-001");
        material.setProductType("RAW_MATERIAL");
        material.setUnitPrice(5.5);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProduct(material);
        item.setQuantity(8.0);
        item.setUnitPrice(6.0);

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setItems(List.of(item));

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
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
    void supplierShipUpdatesStatusAndNotifiesProcurement() {
        Supplier supplier = new Supplier();
        supplier.setId(2L);
        supplier.setEmail("supplier@example.com");

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
        Supplier supplier = new Supplier();
        supplier.setId(3L);
        supplier.setEmail("supplier@example.com");

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

        Warehouse warehouse = new Warehouse();
        warehouse.setId(9L);
        warehouse.setName("原料仓");

        when(purchaseOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByProductId(30L)).thenReturn(List.of());
        when(warehouseRepository.findAll()).thenReturn(List.of(warehouse));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = procurementWorkflowService.warehouseReceive(3L, "warehouse@test.com", "received");

        assertEquals(ProcurementWorkflowService.STATUS_WAREHOUSED, result.getStatus());
        verify(inventoryItemRepository).save(any(InventoryItem.class));
        verify(notificationService).broadcast(eq("/topic/procurement/manager"), any());
    }

    @Test
    void buildSupplierDashboardReturnsSupplierSpecificTodosAndMaterials() {
        Supplier supplier = new Supplier();
        supplier.setId(5L);
        supplier.setName("供应商甲");
        supplier.setCode("SUP-A");
        supplier.setEmail("supplier@example.com");

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

        when(supplierRepository.findByEmailIgnoreCase("supplier@example.com")).thenReturn(Optional.of(supplier));
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
}

