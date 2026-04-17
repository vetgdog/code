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
import com.code.repository.BomItemRepository;
import com.code.repository.BomRepository;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProcurementWeeklyPlanRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.ProductionWeeklyPlanRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.SalesOrderRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class WeeklyPlanningService {

    private static final double PRODUCTION_GROWTH_FACTOR = 1.10;
    private static final double PROCUREMENT_GROWTH_FACTOR = 1.05;

    private final ProductionWeeklyPlanRepository productionWeeklyPlanRepository;
    private final ProcurementWeeklyPlanRepository procurementWeeklyPlanRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final BomRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final NotificationService notificationService;

    public WeeklyPlanningService(ProductionWeeklyPlanRepository productionWeeklyPlanRepository,
                                 ProcurementWeeklyPlanRepository procurementWeeklyPlanRepository,
                                 ProductionPlanRepository productionPlanRepository,
                                 SalesOrderRepository salesOrderRepository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 ProductRepository productRepository,
                                 InventoryItemRepository inventoryItemRepository,
                                 BomRepository bomRepository,
                                 BomItemRepository bomItemRepository,
                                 NotificationService notificationService) {
        this.productionWeeklyPlanRepository = productionWeeklyPlanRepository;
        this.procurementWeeklyPlanRepository = procurementWeeklyPlanRepository;
        this.productionPlanRepository = productionPlanRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.notificationService = notificationService;
    }

    public List<ProductionWeeklyPlan> listProductionPlans() {
        return productionWeeklyPlanRepository.findAllByOrderByWeekStartDesc();
    }

    public List<ProcurementWeeklyPlan> listProcurementPlans() {
        return procurementWeeklyPlanRepository.findAllByOrderByWeekStartDesc();
    }

    @Transactional
    public ProductionWeeklyPlan getOrGenerateProductionPlan(LocalDate referenceDate, String operator) {
        LocalDate weekStart = weekStart(referenceDate == null ? LocalDate.now() : referenceDate);
        return productionWeeklyPlanRepository.findByWeekStart(weekStart)
                .orElseGet(() -> generateProductionPlan(referenceDate, operator));
    }

    @Transactional
    public ProcurementWeeklyPlan getOrGenerateProcurementPlan(LocalDate referenceDate, String operator) {
        LocalDate weekStart = weekStart(referenceDate == null ? LocalDate.now() : referenceDate);
        return procurementWeeklyPlanRepository.findByWeekStart(weekStart)
                .orElseGet(() -> generateProcurementPlan(referenceDate, operator));
    }

    @Transactional
    public ProductionWeeklyPlan generateProductionPlan(LocalDate referenceDate, String operator) {
        LocalDate effectiveDate = referenceDate == null ? LocalDate.now() : referenceDate;
        LocalDate weekStart = weekStart(effectiveDate);
        LocalDate weekEnd = weekEnd(weekStart);
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        LocalDate previousWeekEnd = weekStart.minusDays(1);

        ProductionWeeklyPlan plan = productionWeeklyPlanRepository.findByWeekStart(weekStart)
                .orElseGet(ProductionWeeklyPlan::new);
        plan.setWeekStart(weekStart);
        plan.setWeekEnd(weekEnd);
        plan.setBasedOnWeekStart(previousWeekStart);
        plan.setBasedOnWeekEnd(previousWeekEnd);
        plan.setGeneratedBy(blankToNull(operator) == null ? "system" : operator.trim());
        plan.setGeneratedAt(LocalDateTime.now());
        plan.setStatus("GENERATED");
        plan.setAlgorithmNote("生产计划采用：max(上周完工量×1.10, 当前未完成订单需求) - 当前可用成品库存。");

        Map<Long, Double> lastWeekProduction = aggregateLastWeekProduction(previousWeekStart, previousWeekEnd);
        Map<Long, Double> activeDemand = aggregateActiveSalesDemand();
        Map<Long, Double> availableFinishedInventory = aggregateAvailableInventory("FINISHED_GOOD");
        Map<Long, Product> finishedProducts = productRepository.findAll().stream()
                .filter(product -> "FINISHED_GOOD".equalsIgnoreCase(product.getProductType()))
                .collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left, LinkedHashMap::new));

        Set<Long> candidateIds = Stream.of(lastWeekProduction.keySet(), activeDemand.keySet(), availableFinishedInventory.keySet(), finishedProducts.keySet())
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        List<ProductionWeeklyPlanItem> items = new ArrayList<>();
        for (Long productId : candidateIds) {
            Product product = finishedProducts.get(productId);
            if (product == null) {
                continue;
            }
            double produced = safeNumber(lastWeekProduction.get(productId));
            double demand = safeNumber(activeDemand.get(productId));
            double inventory = safeNumber(availableFinishedInventory.get(productId));
            double baseline = Math.max(round2(produced * PRODUCTION_GROWTH_FACTOR), demand);
            double suggested = Math.max(0.0, round2(baseline - inventory));
            if (suggested <= 0.0) {
                continue;
            }
            ProductionWeeklyPlanItem item = new ProductionWeeklyPlanItem();
            item.setPlan(plan);
            item.setProduct(product);
            item.setSuggestedQuantity(suggested);
            item.setLastWeekProducedQuantity(round2(produced));
            item.setActiveDemandQuantity(round2(demand));
            item.setAvailableInventoryQuantity(round2(inventory));
            item.setBaselineQuantity(round2(baseline));
            item.setGrowthFactor(PRODUCTION_GROWTH_FACTOR);
            item.setSuggestionReason(buildProductionReason(produced, demand, inventory));
            items.add(item);
        }

        items.sort(Comparator.comparing(item -> item.getProduct() == null ? "" : safe(item.getProduct().getName())));
        plan.setItems(items);
        ProductionWeeklyPlan saved = productionWeeklyPlanRepository.save(plan);
        notificationService.broadcast(
                "/topic/production",
                new NotificationMessage(
                        "PRODUCTION_WEEKLY_PLAN_GENERATED",
                        "ProductionWeeklyPlan",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        return saved;
    }

    @Transactional
    public ProcurementWeeklyPlan generateProcurementPlan(LocalDate referenceDate, String operator) {
        LocalDate effectiveDate = referenceDate == null ? LocalDate.now() : referenceDate;
        LocalDate weekStart = weekStart(effectiveDate);
        LocalDate weekEnd = weekEnd(weekStart);
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        LocalDate previousWeekEnd = weekStart.minusDays(1);

        ProcurementWeeklyPlan plan = procurementWeeklyPlanRepository.findByWeekStart(weekStart)
                .orElseGet(ProcurementWeeklyPlan::new);
        plan.setWeekStart(weekStart);
        plan.setWeekEnd(weekEnd);
        plan.setBasedOnWeekStart(previousWeekStart);
        plan.setBasedOnWeekEnd(previousWeekEnd);
        plan.setGeneratedBy(blankToNull(operator) == null ? "system" : operator.trim());
        plan.setGeneratedAt(LocalDateTime.now());
        plan.setStatus("GENERATED");
        plan.setAlgorithmNote("采购计划采用：max(BOM需求, 上周采购量×1.05, 安全库存缺口) - 当前库存 - 在途采购量。");

        ProductionWeeklyPlan productionWeeklyPlan = productionWeeklyPlanRepository.findByWeekStart(weekStart)
                .orElseGet(() -> generateProductionPlan(effectiveDate, operator));
        Map<Long, Double> bomDemand = aggregateBomDemand(productionWeeklyPlan);
        Map<Long, Double> lastWeekProcured = aggregateLastWeekProcurement(previousWeekStart, previousWeekEnd);
        Map<Long, Double> availableRawInventory = aggregateAvailableInventory("RAW_MATERIAL");
        Map<Long, Double> inTransit = aggregateInTransitProcurement();
        Map<Long, Product> rawMaterials = productRepository.findAll().stream()
                .filter(product -> "RAW_MATERIAL".equalsIgnoreCase(product.getProductType()))
                .collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left, LinkedHashMap::new));

        Set<Long> candidateIds = Stream.of(bomDemand.keySet(), lastWeekProcured.keySet(), availableRawInventory.keySet(), inTransit.keySet(), rawMaterials.keySet())
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        List<ProcurementWeeklyPlanItem> items = new ArrayList<>();
        for (Long productId : candidateIds) {
            Product material = rawMaterials.get(productId);
            if (material == null) {
                continue;
            }
            double bomQty = safeNumber(bomDemand.get(productId));
            double procuredQty = safeNumber(lastWeekProcured.get(productId));
            double inventory = safeNumber(availableRawInventory.get(productId));
            double inTransitQty = safeNumber(inTransit.get(productId));
            double safetyGap = Math.max(0.0, round2(safeNumber(material.getSafetyStock()) - inventory));
            double baseline = Math.max(Math.max(round2(procuredQty * PROCUREMENT_GROWTH_FACTOR), bomQty), safetyGap);
            double suggested = Math.max(0.0, round2(baseline - inventory - inTransitQty));
            if (suggested <= 0.0) {
                continue;
            }
            ProcurementWeeklyPlanItem item = new ProcurementWeeklyPlanItem();
            item.setPlan(plan);
            item.setProduct(material);
            item.setPreferredSupplier(blankToNull(material.getPreferredSupplier()));
            item.setSuggestedQuantity(suggested);
            item.setLastWeekProcuredQuantity(round2(procuredQty));
            item.setBomDemandQuantity(round2(bomQty));
            item.setAvailableInventoryQuantity(round2(inventory));
            item.setInTransitQuantity(round2(inTransitQty));
            item.setSafetyStockGap(round2(safetyGap));
            item.setSuggestionReason(buildProcurementReason(bomQty, procuredQty, inventory, inTransitQty, safetyGap));
            items.add(item);
        }

        items.sort(Comparator.comparing(item -> item.getProduct() == null ? "" : safe(item.getProduct().getName())));
        plan.setItems(items);
        ProcurementWeeklyPlan saved = procurementWeeklyPlanRepository.save(plan);
        notificationService.broadcast(
                "/topic/procurement/manager",
                new NotificationMessage(
                        "PROCUREMENT_WEEKLY_PLAN_GENERATED",
                        "ProcurementWeeklyPlan",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        notificationService.broadcast(
                "/topic/procurement",
                new NotificationMessage(
                        "PROCUREMENT_WEEKLY_PLAN_GENERATED",
                        "ProcurementWeeklyPlan",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        return saved;
    }

    @Scheduled(cron = "0 5 1 * * MON")
    @Transactional
    public void autoGenerateWeeklyPlans() {
        LocalDate today = LocalDate.now();
        getOrGenerateProductionPlan(today, "system-auto");
        getOrGenerateProcurementPlan(today, "system-auto");
    }

    private Map<Long, Double> aggregateLastWeekProduction(LocalDate previousWeekStart, LocalDate previousWeekEnd) {
        return productionPlanRepository.findAll().stream()
                .filter(plan -> isCompletedProduction(plan.getStatus()))
                .filter(plan -> withinRange(resolveProductionCompletedDate(plan), previousWeekStart, previousWeekEnd))
                .filter(plan -> plan.getProduct() != null && plan.getProduct().getId() != null)
                .collect(Collectors.groupingBy(
                        plan -> plan.getProduct().getId(),
                        Collectors.summingDouble(plan -> safeNumber(plan.getPlannedQuantity()))
                ));
    }

    private Map<Long, Double> aggregateActiveSalesDemand() {
        return salesOrderRepository.findAll().stream()
                .filter(this::countsAsActiveDemand)
                .flatMap(order -> order.getItems() == null ? Stream.empty() : order.getItems().stream())
                .filter(item -> item.getProduct() != null && item.getProduct().getId() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.summingDouble(item -> safeNumber(item.getQuantity()))
                ));
    }

    private Map<Long, Double> aggregateAvailableInventory(String productType) {
        return inventoryItemRepository.findAll().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getId() != null)
                .filter(item -> productType.equalsIgnoreCase(safe(item.getProduct().getProductType())))
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.summingDouble(item -> Math.max(0.0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity())))
                ));
    }

    private Map<Long, Double> aggregateLastWeekProcurement(LocalDate previousWeekStart, LocalDate previousWeekEnd) {
        return purchaseOrderRepository.findAll().stream()
                .filter(order -> withinRange(resolveProcurementReferenceDate(order), previousWeekStart, previousWeekEnd))
                .flatMap(order -> order.getItems() == null ? Stream.empty() : order.getItems().stream())
                .filter(item -> item.getProduct() != null && item.getProduct().getId() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.summingDouble(item -> safeNumber(item.getQuantity()))
                ));
    }

    private Map<Long, Double> aggregateInTransitProcurement() {
        return purchaseOrderRepository.findAll().stream()
                .filter(this::countsAsInTransit)
                .flatMap(order -> order.getItems() == null ? Stream.empty() : order.getItems().stream())
                .filter(item -> item.getProduct() != null && item.getProduct().getId() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.summingDouble(item -> safeNumber(item.getQuantity()))
                ));
    }

    private Map<Long, Double> aggregateBomDemand(ProductionWeeklyPlan productionWeeklyPlan) {
        if (productionWeeklyPlan == null || productionWeeklyPlan.getItems() == null || productionWeeklyPlan.getItems().isEmpty()) {
            return Map.of();
        }
        Map<Long, Bom> bomByProductId = bomRepository.findAll().stream()
                .filter(bom -> bom.getProduct() != null && bom.getProduct().getId() != null)
                .sorted(Comparator.comparing(Bom::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toMap(bom -> bom.getProduct().getId(), bom -> bom, (left, right) -> left));
        Map<Long, List<BomItem>> bomItemsByBomId = bomItemRepository.findAll().stream()
                .filter(item -> item.getBom() != null && item.getBom().getId() != null)
                .filter(item -> item.getComponentProduct() != null && item.getComponentProduct().getId() != null)
                .collect(Collectors.groupingBy(item -> item.getBom().getId()));

        Map<Long, Double> result = new HashMap<>();
        for (ProductionWeeklyPlanItem item : productionWeeklyPlan.getItems()) {
            Long productId = item.getProduct() == null ? null : item.getProduct().getId();
            if (productId == null) {
                continue;
            }
            Bom bom = bomByProductId.get(productId);
            if (bom == null) {
                continue;
            }
            double outputBase = safeNumber(bom.getQuantity()) <= 0.0 ? 1.0 : safeNumber(bom.getQuantity());
            List<BomItem> bomItems = bomItemsByBomId.getOrDefault(bom.getId(), List.of());
            for (BomItem bomItem : bomItems) {
                Long componentId = bomItem.getComponentProduct().getId();
                double grossDemand = safeNumber(item.getSuggestedQuantity()) * safeNumber(bomItem.getQuantity()) / outputBase;
                result.merge(componentId, grossDemand, Double::sum);
            }
        }
        return result;
    }

    private boolean isCompletedProduction(String status) {
        String normalized = safe(status).toUpperCase(Locale.ROOT);
        return "DONE".equals(normalized) || "WAREHOUSED".equals(normalized);
    }

    private boolean countsAsActiveDemand(SalesOrder order) {
        String normalized = safe(order == null ? null : order.getStatus());
        return !normalized.isEmpty()
                && !List.of("已完成", "已拒绝", "已发货").contains(normalized);
    }

    private boolean countsAsInTransit(PurchaseOrder order) {
        String normalized = safe(order == null ? null : order.getStatus());
        return List.of(
                ProcurementWorkflowService.STATUS_WAITING_SUPPLIER,
                ProcurementWorkflowService.STATUS_SUPPLIER_ACCEPTED,
                ProcurementWorkflowService.STATUS_SUPPLIER_SHIPPED,
                ProcurementWorkflowService.STATUS_WAITING_WAREHOUSE_RECEIPT
        ).contains(normalized);
    }

    private LocalDate resolveProductionCompletedDate(ProductionPlan plan) {
        if (plan == null) {
            return null;
        }
        LocalDateTime completedAt = plan.getEndDate() == null ? plan.getCreatedAt() : plan.getEndDate();
        return completedAt == null ? null : completedAt.toLocalDate();
    }

    private LocalDate resolveProcurementReferenceDate(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        LocalDateTime value = order.getReceivedAt() != null ? order.getReceivedAt() : order.getOrderDate();
        return value == null ? null : value.toLocalDate();
    }

    private boolean withinRange(LocalDate value, LocalDate start, LocalDate end) {
        return value != null && (value.isEqual(start) || value.isAfter(start)) && (value.isEqual(end) || value.isBefore(end));
    }

    private LocalDate weekStart(LocalDate date) {
        return (date == null ? LocalDate.now() : date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate weekEnd(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }

    private String buildProductionReason(double produced, double demand, double inventory) {
        return String.format(Locale.ROOT,
                "上周完工 %.2f，本周未完成需求 %.2f，当前可用库存 %.2f，按 10%% 增长系数测算。",
                round2(produced), round2(demand), round2(inventory));
    }

    private String buildProcurementReason(double bomDemand, double procured, double inventory, double inTransit, double safetyGap) {
        return String.format(Locale.ROOT,
                "BOM需求 %.2f，上周采购 %.2f，当前库存 %.2f，在途 %.2f，安全库存缺口 %.2f。",
                round2(bomDemand), round2(procured), round2(inventory), round2(inTransit), round2(safetyGap));
    }

    private double safeNumber(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String blankToNull(String value) {
        return safe(value).isEmpty() ? null : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

