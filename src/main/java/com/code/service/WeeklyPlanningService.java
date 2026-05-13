package com.code.service;

import com.code.entity.Bom;
import com.code.entity.BomItem;
import com.code.entity.Product;
import com.code.entity.ProcurementWeeklyPlan;
import com.code.entity.ProcurementWeeklyPlanItem;
import com.code.entity.ProductionPlan;
import com.code.entity.ProductionWeeklyPlan;
import com.code.entity.ProductionWeeklyPlanItem;
import com.code.entity.PurchaseOrder;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 周计划生成服务。
 *
 * <p>该服务负责把“历史生产/采购事实 + 当前未完成需求 + 当前库存 + 在途量/BOM 结构”组合成两类周计划：
 * 生产周计划和采购周计划。它本质上是一个轻量级 MRP/MPS 计算器，不直接执行生产或采购，而是生成管理层可确认的建议单据。</p>
 *
 * <p>实现上采用“按周一对齐自然周、按公式生成建议数量、生成后通过 WebSocket 广播”的模式，适合教学型 ERP 场景。
 * 如果未来计划逻辑更复杂，通常会继续拆分成独立策略类或规则引擎。</p>
 */
@Service
public class WeeklyPlanningService {

    /**
     * 生产计划增长系数：默认按上周完工量上浮 10%。
     */
    private static final double PRODUCTION_GROWTH_FACTOR = 1.10;

    /**
     * 采购计划增长系数：默认按上周采购量上浮 5%。
     */
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

    /**
     * 通过构造器注入依赖，保证服务本身无状态，便于测试、事务代理和定时任务复用。
     */
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

        // “先查再生成”保证同一自然周只有一份周计划，
        // 避免用户反复点生成导致同周重复计划并存。
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

        // 生产周计划同时考虑三类信息：
        // 1) 上周实际完工量 -> 反映历史产能/节奏；
        // 2) 当前未完成订单需求 -> 反映现实业务压力；
        // 3) 当前可用成品库存 -> 避免重复建议生产。
        Map<Long, Double> lastWeekProduction = aggregateLastWeekProduction(previousWeekStart, previousWeekEnd);
        Map<Long, Double> activeDemand = aggregateActiveSalesDemand();
        Map<Long, Double> availableFinishedInventory = aggregateAvailableInventory("FINISHED_GOOD");
        Map<Long, Product> finishedProducts = productRepository.findAll().stream()
                .filter(product -> "FINISHED_GOOD".equalsIgnoreCase(product.getProductType()))
                .collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left, LinkedHashMap::new));

        Set<Long> candidateIds = Stream.of(lastWeekProduction.keySet(), activeDemand.keySet(), availableFinishedInventory.keySet(), finishedProducts.keySet())
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // candidateIds 取并集而不是只看有需求的产品，
        // 是为了让“有历史生产但本周库存异常”或“刚建品但无历史数据”等情况也能进入统一计算口径。
        List<ProductionWeeklyPlanItem> items = new ArrayList<>();
        for (Long productId : candidateIds) {
            Product product = finishedProducts.get(productId);
            if (product == null) {
                continue;
            }
            double produced = safeNumber(lastWeekProduction.get(productId));
            double demand = safeNumber(activeDemand.get(productId));
            double inventory = safeNumber(availableFinishedInventory.get(productId));

            // baseline = max(历史趋势, 当前真实需求)
            // suggested = baseline - 当前可用库存
            // 这个公式体现“历史经验 + 现实订单”的折中策略，而不是纯预测或纯按单排产。
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

        // replacePlanItems 采用“尽量原地替换”而不是直接 set 新集合，
        // 是为了兼容 JPA 托管集合场景，减少 orphan/remove 行为的不确定性。
        replacePlanItems(plan::getItems, plan::setItems, items);
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

        // 采购周计划依赖生产周计划，因为原材料需求首先来自于要生产多少成品。
        // 若本周生产周计划尚不存在，这里会先自动补生成，确保两类计划口径一致。
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

            // 采购基线同时考虑三种压力：
            // 1) 生产 BOM 真实消耗；
            // 2) 上周采购节奏；
            // 3) 安全库存缺口。
            // 最终再扣掉现有库存和在途量，避免过度采购。
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
        replacePlanItems(plan::getItems, plan::setItems, items);
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

        // 每周一凌晨自动生成，目的是在业务周开始前把建议计划准备好，
        // 让生产经理/采购经理上班后能直接查看本周建议，而不是手工点击触发。
        LocalDate today = LocalDate.now();
        getOrGenerateProductionPlan(today, "system-auto");
        getOrGenerateProcurementPlan(today, "system-auto");
    }

    /**
     * 统计上周已完工/已入库的生产数量，作为本周生产建议的历史基线。
     */
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

    /**
     * 汇总当前仍会影响交付的销售需求。
     *
     * <p>这里把未完成订单明细直接累计为 active demand，属于较直接的需求拉动计算方式。</p>
     */
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

    /**
     * 汇总某类产品的可用库存。
     *
     * <p>这里显式扣除 reservedQuantity，说明计划系统看的是“还能继续用于未来决策的库存”，
     * 而不是账面总库存。</p>
     */
    private Map<Long, Double> aggregateAvailableInventory(String productType) {
        return inventoryItemRepository.findAll().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getId() != null)
                .filter(item -> productType.equalsIgnoreCase(safe(item.getProduct().getProductType())))
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.summingDouble(item -> Math.max(0.0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity())))
                ));
    }

    /**
     * 汇总上周采购量，用于形成采购增长基线。
     */
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

    /**
     * 汇总尚未入库的在途采购量，避免周计划重复建议采购已经下单但未到货的原材料。
     */
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

    /**
     * 根据生产周计划展开 BOM 需求。
     *
     * <p>这是采购周计划最关键的一步：把“建议生产多少成品”转换成“理论上需要多少原材料”。
     * 当前实现按 BOM 标准用量做线性展开，没有考虑损耗率、替代料、批量起订量等更复杂因素。</p>
     */
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

    /**
     * 生产完成状态识别：只有 DONE / WAREHOUSED 才计入上周实际产出。
     */
    private boolean isCompletedProduction(String status) {
        String normalized = safe(status).toUpperCase(Locale.ROOT);
        return "DONE".equals(normalized) || "WAREHOUSED".equals(normalized);
    }

    /**
     * 判断销售订单是否仍应计入有效需求。
     *
     * <p>已完成、已拒绝、已发货不再视作未来待满足需求，其余状态默认仍会消耗供给能力。</p>
     */
    private boolean countsAsActiveDemand(SalesOrder order) {
        String normalized = safe(order == null ? null : order.getStatus());
        return !normalized.isEmpty()
                && !List.of("已完成", "已拒绝", "已发货").contains(normalized);
    }

    /**
     * 判断采购单是否仍属于“在途”状态。
     */
    private boolean countsAsInTransit(PurchaseOrder order) {
        String normalized = safe(order == null ? null : order.getStatus());
        return List.of(
                ProcurementWorkflowService.STATUS_WAITING_SUPPLIER,
                ProcurementWorkflowService.STATUS_SUPPLIER_ACCEPTED,
                ProcurementWorkflowService.STATUS_SUPPLIER_SHIPPED,
                ProcurementWorkflowService.STATUS_WAITING_WAREHOUSE_RECEIPT
        ).contains(normalized);
    }

    /**
     * 解析生产计划完工参考日期：优先结束时间，缺失时回退创建时间，兼容旧数据。
     */
    private LocalDate resolveProductionCompletedDate(ProductionPlan plan) {
        if (plan == null) {
            return null;
        }
        LocalDateTime completedAt = plan.getEndDate() == null ? plan.getCreatedAt() : plan.getEndDate();
        return completedAt == null ? null : completedAt.toLocalDate();
    }

    /**
     * 解析采购参考日期：优先收货时间，未收货时退回下单时间。
     */
    private LocalDate resolveProcurementReferenceDate(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        LocalDateTime value = order.getReceivedAt() != null ? order.getReceivedAt() : order.getOrderDate();
        return value == null ? null : value.toLocalDate();
    }

    /**
     * 判断日期是否落在包含式区间内。
     */
    private boolean withinRange(LocalDate value, LocalDate start, LocalDate end) {
        return value != null && (value.isEqual(start) || value.isAfter(start)) && (value.isEqual(end) || value.isBefore(end));
    }

    /**
     * 将任意日期归一到所在周的周一，作为周计划主键维度。
     */
    private LocalDate weekStart(LocalDate date) {
        return (date == null ? LocalDate.now() : date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * 根据周一起始日推算周日结束日。
     */
    private LocalDate weekEnd(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }

    /**
     * 生成生产周计划项的解释文本，帮助使用者理解建议值从何而来。
     */
    private String buildProductionReason(double produced, double demand, double inventory) {
        return String.format(Locale.ROOT,
                "上周完工 %.2f，本周未完成需求 %.2f，当前可用库存 %.2f，按 10%% 增长系数测算。",
                round2(produced), round2(demand), round2(inventory));
    }

    /**
     * 生成采购周计划项的解释文本，把 BOM 需求、历史采购、库存与在途量并列展示。
     */
    private String buildProcurementReason(double bomDemand, double procured, double inventory, double inTransit, double safetyGap) {
        return String.format(Locale.ROOT,
                "BOM需求 %.2f，上周采购 %.2f，当前库存 %.2f，在途 %.2f，安全库存缺口 %.2f。",
                round2(bomDemand), round2(procured), round2(inventory), round2(inTransit), round2(safetyGap));
    }

    /**
     * 尽量原地替换计划明细集合，兼容 JPA 托管集合与不可变集合两种情况。
     */
    private <T> void replacePlanItems(Supplier<List<T>> getter, Consumer<List<T>> setter, List<T> newItems) {
        List<T> existing = getter.get();
        if (existing == null) {
            setter.accept(new ArrayList<>(newItems));
            return;
        }
        try {
            existing.clear();
            existing.addAll(newItems);
        } catch (UnsupportedOperationException ex) {
            setter.accept(new ArrayList<>(newItems));
        }
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

