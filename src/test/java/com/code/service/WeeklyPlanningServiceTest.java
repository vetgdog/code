package com.code.service;

import com.code.entity.Bom;
import com.code.entity.BomItem;
import com.code.entity.InventoryItem;
import com.code.entity.OrderItem;
import com.code.entity.Product;
import com.code.entity.ProcurementWeeklyPlan;
import com.code.entity.ProcurementWeeklyPlanItem;
import com.code.entity.ProductionPlan;
import com.code.entity.ProductionWeeklyPlan;
import com.code.entity.ProductionWeeklyPlanItem;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseOrderItem;
import com.code.entity.SalesOrder;
import com.code.entity.Warehouse;
import com.code.repository.BomItemRepository;
import com.code.repository.BomRepository;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProcurementWeeklyPlanRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.ProductionWeeklyPlanRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.SalesOrderRepository;
import com.code.websocket.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanningServiceTest {

    @InjectMocks
    private WeeklyPlanningService weeklyPlanningService;

    @Mock
    private ProductionWeeklyPlanRepository productionWeeklyPlanRepository;
    @Mock
    private ProcurementWeeklyPlanRepository procurementWeeklyPlanRepository;
    @Mock
    private ProductionPlanRepository productionPlanRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private BomRepository bomRepository;
    @Mock
    private BomItemRepository bomItemRepository;
    @Mock
    private NotificationService notificationService;

    @Test
    void generateProductionPlanShouldUseLastWeekProductionAndDemandAdjustedByInventory() {
        LocalDate referenceDate = LocalDate.of(2026, 4, 15);
        LocalDate weekStart = LocalDate.of(2026, 4, 13);

        Product finished = new Product();
        finished.setId(1L);
        finished.setName("成品机柜");
        finished.setSku("FG-001");
        finished.setProductType("FINISHED_GOOD");

        ProductionPlan lastWeekPlan = new ProductionPlan();
        lastWeekPlan.setProduct(finished);
        lastWeekPlan.setPlannedQuantity(10.0);
        lastWeekPlan.setStatus("WAREHOUSED");
        lastWeekPlan.setEndDate(LocalDateTime.of(2026, 4, 10, 10, 0));

        SalesOrder activeOrder = new SalesOrder();
        activeOrder.setStatus(OrderWorkflowService.STATUS_PENDING_WAREHOUSE_CHECK);
        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(finished);
        orderItem.setQuantity(8.0);
        activeOrder.setItems(List.of(orderItem));

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        InventoryItem stock = new InventoryItem();
        stock.setProduct(finished);
        stock.setWarehouse(warehouse);
        stock.setQuantity(3.0);
        stock.setReservedQuantity(0.0);

        when(productionWeeklyPlanRepository.findByWeekStart(weekStart)).thenReturn(Optional.empty());
        when(productionPlanRepository.findAll()).thenReturn(List.of(lastWeekPlan));
        when(salesOrderRepository.findAll()).thenReturn(List.of(activeOrder));
        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(inventoryItemRepository.findAll()).thenReturn(List.of(stock));
        when(productionWeeklyPlanRepository.save(any(ProductionWeeklyPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionWeeklyPlan plan = weeklyPlanningService.generateProductionPlan(referenceDate, "planner@test.com");

        assertNotNull(plan);
        assertEquals(weekStart, plan.getWeekStart());
        assertEquals(1, plan.getItems().size());
        ProductionWeeklyPlanItem item = plan.getItems().get(0);
        assertEquals(11.0, item.getBaselineQuantity());
        assertEquals(8.0, item.getSuggestedQuantity());
        assertEquals(10.0, item.getLastWeekProducedQuantity());
        assertEquals(8.0, item.getActiveDemandQuantity());
        verify(notificationService).broadcast(eq("/topic/production"), any());
    }

    @Test
    void generateProcurementPlanShouldUseBomDemandAndSubtractInventoryAndInTransit() {
        LocalDate referenceDate = LocalDate.of(2026, 4, 15);
        LocalDate weekStart = LocalDate.of(2026, 4, 13);

        Product finished = new Product();
        finished.setId(10L);
        finished.setName("成品机柜");
        finished.setSku("FG-010");
        finished.setProductType("FINISHED_GOOD");

        Product raw = new Product();
        raw.setId(20L);
        raw.setName("冷轧钢板");
        raw.setSku("RM-020");
        raw.setProductType("RAW_MATERIAL");
        raw.setPreferredSupplier("华东钢材供应商");
        raw.setSafetyStock(5.0);

        ProductionWeeklyPlan currentProductionPlan = new ProductionWeeklyPlan();
        currentProductionPlan.setWeekStart(weekStart);
        ProductionWeeklyPlanItem productionItem = new ProductionWeeklyPlanItem();
        productionItem.setProduct(finished);
        productionItem.setSuggestedQuantity(10.0);
        productionItem.setPlan(currentProductionPlan);
        currentProductionPlan.setItems(List.of(productionItem));

        Bom bom = new Bom();
        bom.setId(101L);
        bom.setProduct(finished);
        bom.setQuantity(1.0);
        bom.setCreatedAt(LocalDateTime.of(2026, 4, 1, 8, 0));

        BomItem bomItem = new BomItem();
        bomItem.setBom(bom);
        bomItem.setComponentProduct(raw);
        bomItem.setQuantity(2.0);

        PurchaseOrder previousWeekOrder = new PurchaseOrder();
        previousWeekOrder.setStatus(ProcurementWorkflowService.STATUS_WAREHOUSED);
        previousWeekOrder.setReceivedAt(LocalDateTime.of(2026, 4, 9, 9, 0));
        PurchaseOrderItem previousWeekItem = new PurchaseOrderItem();
        previousWeekItem.setProduct(raw);
        previousWeekItem.setQuantity(4.0);
        previousWeekOrder.setItems(List.of(previousWeekItem));

        PurchaseOrder inTransitOrder = new PurchaseOrder();
        inTransitOrder.setStatus(ProcurementWorkflowService.STATUS_SUPPLIER_SHIPPED);
        PurchaseOrderItem inTransitItem = new PurchaseOrderItem();
        inTransitItem.setProduct(raw);
        inTransitItem.setQuantity(1.0);
        inTransitOrder.setItems(List.of(inTransitItem));

        InventoryItem rawInventory = new InventoryItem();
        rawInventory.setProduct(raw);
        rawInventory.setQuantity(2.0);
        rawInventory.setReservedQuantity(0.0);

        when(procurementWeeklyPlanRepository.findByWeekStart(weekStart)).thenReturn(Optional.empty());
        when(productionWeeklyPlanRepository.findByWeekStart(weekStart)).thenReturn(Optional.of(currentProductionPlan));
        when(bomRepository.findAll()).thenReturn(List.of(bom));
        when(bomItemRepository.findAll()).thenReturn(List.of(bomItem));
        when(purchaseOrderRepository.findAll()).thenReturn(List.of(previousWeekOrder, inTransitOrder));
        when(productRepository.findAll()).thenReturn(List.of(raw));
        when(inventoryItemRepository.findAll()).thenReturn(List.of(rawInventory));
        when(procurementWeeklyPlanRepository.save(any(ProcurementWeeklyPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcurementWeeklyPlan plan = weeklyPlanningService.generateProcurementPlan(referenceDate, "buyer@test.com");

        assertNotNull(plan);
        assertEquals(weekStart, plan.getWeekStart());
        assertEquals(1, plan.getItems().size());
        ProcurementWeeklyPlanItem item = plan.getItems().get(0);
        assertEquals(20.0, item.getBomDemandQuantity());
        assertEquals(4.0, item.getLastWeekProcuredQuantity());
        assertEquals(2.0, item.getAvailableInventoryQuantity());
        assertEquals(1.0, item.getInTransitQuantity());
        assertEquals(17.0, item.getSuggestedQuantity());
        assertEquals("华东钢材供应商", item.getPreferredSupplier());
        assertTrue(item.getSuggestionReason().contains("BOM需求"));
        verify(notificationService).broadcast(eq("/topic/procurement/manager"), any());
        verify(notificationService).broadcast(eq("/topic/procurement"), any());
    }

    @Test
    void generateProductionPlanShouldReplaceExistingItemsInPlaceWhenSameWeekRegenerated() {
        LocalDate referenceDate = LocalDate.of(2026, 5, 7);
        LocalDate weekStart = LocalDate.of(2026, 5, 4);

        Product finished = new Product();
        finished.setId(1L);
        finished.setName("成品机柜");
        finished.setSku("FG-001");
        finished.setProductType("FINISHED_GOOD");

        ProductionWeeklyPlan existingPlan = new ProductionWeeklyPlan();
        existingPlan.setId(99L);
        existingPlan.setWeekStart(weekStart);
        existingPlan.setItems(new ArrayList<>());

        ProductionWeeklyPlanItem staleItem = new ProductionWeeklyPlanItem();
        staleItem.setPlan(existingPlan);
        staleItem.setProduct(finished);
        staleItem.setSuggestedQuantity(1.0);
        existingPlan.getItems().add(staleItem);

        ProductionPlan lastWeekPlan = new ProductionPlan();
        lastWeekPlan.setProduct(finished);
        lastWeekPlan.setPlannedQuantity(20.0);
        lastWeekPlan.setStatus("DONE");
        lastWeekPlan.setEndDate(LocalDateTime.of(2026, 5, 1, 10, 0));

        when(productionWeeklyPlanRepository.findByWeekStart(weekStart)).thenReturn(Optional.of(existingPlan));
        when(productionPlanRepository.findAll()).thenReturn(List.of(lastWeekPlan));
        when(salesOrderRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(inventoryItemRepository.findAll()).thenReturn(List.of());
        when(productionWeeklyPlanRepository.save(any(ProductionWeeklyPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionWeeklyPlan regenerated = weeklyPlanningService.generateProductionPlan(referenceDate, "planner@test.com");

        assertEquals(existingPlan, regenerated);
        assertEquals(1, regenerated.getItems().size());
        assertEquals(22.0, regenerated.getItems().get(0).getSuggestedQuantity());
        assertEquals(existingPlan, regenerated.getItems().get(0).getPlan());
    }
}


