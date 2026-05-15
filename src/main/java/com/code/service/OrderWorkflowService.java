package com.code.service;

import com.code.entity.Batch;
import com.code.entity.InventoryItem;
import com.code.entity.OrderItem;
import com.code.entity.ProductionPlan;
import com.code.entity.Product;
import com.code.entity.SalesOrder;
import com.code.entity.StockTransaction;
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
import com.code.support.WarehouseDefaults;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
/*
 * 订单履约工作流服务。
 *
 * <p>这是整个系统最核心的业务编排服务之一，负责驱动销售订单从“创建”到“发货”的完整履约流程。
 * 它串联了销售、仓库、生产、质检、库存、批次、消息通知等多个模块，
 * 本质上实现的是一个轻量级订单状态机（State Machine）。</p>
 *
 * <p>在企业架构中，这类服务通常属于“应用服务 / 流程服务”层：
 * 它不专注于某一个实体的简单增删改查，而是负责跨聚合协调多个资源，
 * 例如 SalesOrder、ProductionPlan、InventoryItem、Batch、StockTransaction 同步变化。</p>
 */
public class OrderWorkflowService {

    public static final String STATUS_PENDING_SALES_REVIEW = "待销售审核";
    public static final String STATUS_PENDING_WAREHOUSE_CHECK = "待仓库核查";
    public static final String STATUS_ACCEPTED = "已接单";
    public static final String STATUS_IN_PRODUCTION = "生产中";
    public static final String STATUS_PENDING_QUALITY = "待质检";
    public static final String STATUS_PENDING_PRODUCTION_STOCK_IN = "待生产入库";
    public static final String STATUS_SHIPPED = "已发货";

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProductionMaterialRequestService productionMaterialRequestService;

    @Transactional
    /*
     * 销售审核通过后，将订单路由给仓库核查。
     *
     * <p>这是订单进入正式履约流程的第一步。只有当订单处于“待销售审核”或初始 NEW 状态时才允许流转，
     * 否则说明该订单已经进入其它业务阶段，重复提交会破坏状态机一致性。</p>
     */
    public SalesOrder routeToWarehouseCheck(Long orderId, String operator) {
        SalesOrder order = getOrder(orderId);
        String currentStatus = safeStatus(order.getStatus());
        if (!STATUS_PENDING_SALES_REVIEW.equals(currentStatus) && !"NEW".equals(currentStatus.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许提交仓库核查: " + order.getStatus());
        }

        order.setStatus(STATUS_PENDING_WAREHOUSE_CHECK);
        order.setCreatedAt(order.getCreatedAt() == null ? LocalDateTime.now() : order.getCreatedAt());
        SalesOrder saved = salesOrderRepository.save(order);

        notificationService.broadcast(
                "/topic/orders/warehouse",
                buildOrderMessage(
                        "WAREHOUSE_ACTION_REQUIRED",
                        saved,
                        operator,
                        "接收一条新的订单 " + saved.getOrderNo() + "，请查看。",
                        saved.getShippingAddress()
                )
        );
        notificationService.broadcast("/topic/orders", buildOrderMessage("ORDER_WORKFLOW_UPDATED", saved, operator));

        return saved;
    }

    @Transactional
    /*
     * 仓库核查订单。
     *
     * <p>该方法是订单流程中最关键的分流点：
     * - 如果库存充足：直接锁定库存，订单进入“已接单”；
     * - 如果库存不足：自动创建生产计划，订单进入“生产中”。</p>
     *
     * <p>这里使用事务的原因非常明确：库存锁定、订单状态更新、生产计划创建必须保持原子性，
     * 任一环节失败都不能只执行一半，否则极易出现“状态变了但库存没锁住”的脏数据。</p>
     */
    public WarehouseReviewResult warehouseReview(Long orderId, String operator, String note) {
        SalesOrder order = getOrder(orderId);
        String currentStatus = safeStatus(order.getStatus());
        if (!STATUS_PENDING_WAREHOUSE_CHECK.equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许仓库核查: " + order.getStatus());
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单没有明细，无法执行库存核查");
        }

        List<ProductShortage> shortages = evaluateShortages(order);
        List<ProductionPlan> createdPlans = new ArrayList<>();
        User operatorUser = resolveUserByEmail(operator);

        if (shortages.isEmpty()) {
            reserveInventoryForOrder(order);
            order.setStatus(STATUS_ACCEPTED);
            notificationService.broadcast(
                    "/topic/orders/sales",
                    buildOrderMessage(
                            "ORDER_READY_TO_SHIP",
                            order,
                            operator,
                            "订单 " + order.getOrderNo() + " 库存已核验通过，请查看。",
                            note
                    )
            );
        } else {
            order.setStatus(STATUS_IN_PRODUCTION);
            createdPlans = createProductionPlans(order, shortages, operatorUser);
            notificationService.broadcast(
                    "/topic/orders/production",
                    buildReviewMessage(
                            "ORDER_PRODUCTION_REQUIRED",
                            order,
                            shortages,
                            createdPlans,
                            operator,
                            note,
                            "生产计划订单 " + summarizePlanNos(createdPlans) + " 已下发，请查看。",
                            note
                    )
            );
            notificationService.broadcast(
                    "/topic/orders/warehouse",
                    buildReviewMessage(
                            "ORDER_PRODUCTION_REQUIRED",
                            order,
                            shortages,
                            createdPlans,
                            operator,
                            note,
                            "已将生产计划发送给生产管理员。",
                            "订单 " + order.getOrderNo() + " 缺货，生产计划 " + summarizePlanNos(createdPlans) + " 已下发。"
                    )
            );
        }

        SalesOrder savedOrder = salesOrderRepository.save(order);
        WarehouseReviewResult result = new WarehouseReviewResult(savedOrder, shortages, createdPlans, note);
        notificationService.broadcast("/topic/orders", new NotificationMessage("ORDER_WORKFLOW_UPDATED", "SalesOrder", savedOrder.getId(), result, LocalDateTime.now()));
        return result;
    }

    @Transactional
    /*
     * 生产完工回传。
     *
     * <p>完工后系统不会立即把成品计入库存，而是先：
     * 1. 把生产计划状态改为 DONE；
     * 2. 生成待质检批次；
     * 3. 更新订单状态为“待质检”；
     * 4. 推送质检通知。</p>
     *
     * <p>这体现了企业制造系统中非常重要的分层：
     * 生产完工 ≠ 质检合格 ≠ 仓库入库，三者必须分步处理。</p>
     */
    public SalesOrder markProductionCompleted(Long orderId, String operator, String note) {
        SalesOrder order = getOrder(orderId);
        if (!STATUS_IN_PRODUCTION.equals(safeStatus(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许生产完工回传: " + order.getStatus());
        }

        productionMaterialRequestService.requireReadyRequestForOrder(orderId);

        List<ProductionPlan> plans = productionPlanRepository.findByPlanNoStartingWith("PLAN-" + order.getOrderNo() + "-");
        User productionManager = resolveUserByEmail(operator);
        for (ProductionPlan plan : plans) {
            plan.setStatus("DONE");
            plan.setEndDate(LocalDateTime.now());
            plan.setCompletedById(productionManager == null ? null : productionManager.getId());
            plan.setCompletedByEmail(blankToNull(operator));
            plan.setCompletedByName(resolveDisplayName(productionManager, operator));
        }
        if (!plans.isEmpty()) {
            productionPlanRepository.saveAll(plans);
        }

        prepareCompletedPlanBatchesForQuality(order, plans);
        productionMaterialRequestService.markProductionCompleted(orderId);
        order.setStatus(STATUS_PENDING_QUALITY);
        SalesOrder saved = salesOrderRepository.save(order);
        NotificationMessage completionMessage = buildReviewMessage(
                "ORDER_PRODUCTION_DONE",
                saved,
                List.of(),
                plans,
                operator,
                note,
                "生产计划订单 " + summarizePlanNos(plans) + " 已完成，请质检员检验！",
                note
        );
        notificationService.broadcast("/topic/quality", completionMessage);
        notificationService.broadcast("/topic/orders", buildOrderMessage("ORDER_WORKFLOW_UPDATED", saved, operator));
        return saved;
    }

    @Transactional
    /*
     * 仓库确认生产入库。
     *
     * <p>该方法会把质检合格的成品批次正式计入库存，并将生产计划状态改为 WAREHOUSED。
     * 随后再次检查订单缺口是否已被补齐，如果补齐则把订单推进到“已接单/可发货”状态。</p>
     */
    public SalesOrder confirmProductionStockIn(Long orderId, String operator, String note) {
        SalesOrder order = getOrder(orderId);
        if (!STATUS_PENDING_PRODUCTION_STOCK_IN.equals(safeStatus(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许确认生产入库: " + order.getStatus());
        }

        String prefix = "PLAN-" + order.getOrderNo() + "-";
        List<ProductionPlan> completedPlans = productionPlanRepository.findByPlanNoStartingWithAndStatus(prefix, "DONE");
        if (completedPlans.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前订单没有待入库的完工生产计划");
        }

        List<ProductionPlan> stockedPlans = stockInCompletedPlans(order, operator, completedPlans);
        List<ProductShortage> shortages = evaluateShortages(order);
        if (!shortages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "生产入库后库存仍不足，请检查生产计划是否全部完工");
        }
        reserveInventoryForOrder(order);
        order.setStatus(STATUS_ACCEPTED);
        SalesOrder saved = salesOrderRepository.save(order);
        productionMaterialRequestService.markWarehoused(orderId);

        notificationService.broadcast(
                "/topic/orders/sales",
                buildReviewMessage(
                        "PRODUCTION_STOCK_IN_CONFIRMED",
                        saved,
                        List.of(),
                        stockedPlans,
                        operator,
                        note,
                        "生产计划订单 " + summarizePlanNos(stockedPlans) + " 已完成入库，请查看。",
                        note
                )
        );
        notificationService.broadcast("/topic/orders", buildOrderMessage("ORDER_WORKFLOW_UPDATED", saved, operator));
        return saved;
    }

    @Transactional
    public ProductionPlan completeInventoryAlertPlan(Long planId, String operator, String note) {
        ProductionPlan plan = getProductionPlan(planId);
        requireInventoryAlertPlan(plan);
        return completeStandalonePlan(plan, operator, note);
    }

    @Transactional
    public ProductionPlan completeStandaloneProductionPlan(Long planId, String operator, String note) {
        ProductionPlan plan = getProductionPlan(planId);
        requireStandaloneProductionPlan(plan);
        return completeStandalonePlan(plan, operator, note);
    }

    private ProductionPlan completeStandalonePlan(ProductionPlan plan, String operator, String note) {
        String currentStatus = safeStatus(plan.getStatus()).toUpperCase(Locale.ROOT);
        if ("DONE".equals(currentStatus) || "WAREHOUSED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许执行生产计划完工: " + plan.getStatus());
        }

        productionMaterialRequestService.requireReadyRequestForPlan(plan.getId());

        User productionManager = resolveUserByEmail(operator);
        plan.setStatus("DONE");
        plan.setEndDate(LocalDateTime.now());
        plan.setCompletedById(productionManager == null ? null : productionManager.getId());
        plan.setCompletedByEmail(blankToNull(operator));
        plan.setCompletedByName(resolveDisplayName(productionManager, operator));
        ProductionPlan saved = productionPlanRepository.save(plan);

        prepareCompletedPlanBatchesForQuality(null, List.of(saved));
        productionMaterialRequestService.markProductionCompletedForPlan(plan.getId());
        notificationService.broadcast(
                "/topic/production",
                new NotificationMessage(
                        "STANDALONE_PRODUCTION_PLAN_DONE",
                        "ProductionPlan",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        return saved;
    }

    public List<ProductionPlan> listStandalonePlansPendingStockIn() {
        return productionPlanRepository.findAll().stream()
                .filter(this::isStandaloneProductionPlan)
                .filter(plan -> "DONE".equalsIgnoreCase(safeStatus(plan.getStatus())))
                .filter(this::hasPassedQualityBatchForStandalonePlan)
                .sorted(Comparator.comparing(ProductionPlan::getEndDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductionPlan::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<ProductionPlan> listInventoryAlertPlansPendingStockIn() {
        return listStandalonePlansPendingStockIn().stream()
                .filter(this::isInventoryAlertPlan)
                .toList();
    }

    @Transactional
    public ProductionPlan confirmInventoryAlertPlanStockIn(Long planId, String operator, String note) {
        ProductionPlan plan = getProductionPlan(planId);
        requireInventoryAlertPlan(plan);
        return confirmStandalonePlanStockIn(plan, operator, note);
    }

    @Transactional
    public ProductionPlan confirmStandalonePlanStockIn(Long planId, String operator, String note) {
        ProductionPlan plan = getProductionPlan(planId);
        requireStandaloneProductionPlan(plan);
        return confirmStandalonePlanStockIn(plan, operator, note);
    }

    private ProductionPlan confirmStandalonePlanStockIn(ProductionPlan plan, String operator, String note) {
        if (!"DONE".equalsIgnoreCase(safeStatus(plan.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许确认生产计划入库: " + plan.getStatus());
        }

        Product product = plan.getProduct();
        if (product == null || product.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生产计划缺少产品信息，无法入库");
        }

        Batch batch = batchRepository.findBySourceOrderNoAndProductId(plan.getPlanNo(), product.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "未找到对应质检批次，无法确认入库"));
        if (!QualityService.STATUS_PASSED.equals(safeStatus(batch.getQualityStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "批次 " + batch.getBatchNo() + " 尚未质检合格，不能入库");
        }

        double quantity = safeNumber(plan.getPlannedQuantity());
        if (quantity > 0) {
            Warehouse warehouse = resolveWarehouseForProduct(product.getId());
            InventoryItem item = findOrCreateInventoryItem(product, warehouse);
            item.setQuantity(safeNumber(item.getQuantity()) + quantity);
            item.setLot(batch.getBatchNo());
            inventoryItemRepository.save(item);

            User operatorUser = resolveUserByEmail(operator);
            createStockTransaction(
                    product,
                    warehouse,
                    quantity,
                    "IN",
                    "PRODUCTION_PLAN",
                    plan.getId(),
                    operatorUser,
                    noteOrDefault(blankToNull(note), isInventoryAlertPlan(plan) ? "库存预警补产成品入库" : "手动生产计划成品入库"),
                    batch.getBatchNo()
            );
        }

        plan.setStatus("WAREHOUSED");
        ProductionPlan saved = productionPlanRepository.save(plan);
        productionMaterialRequestService.markWarehousedForPlan(plan.getId());
        notificationService.broadcast(
                "/topic/production",
                new NotificationMessage(
                        "STANDALONE_PRODUCTION_PLAN_WAREHOUSED",
                        "ProductionPlan",
                        saved.getId(),
                        saved,
                        LocalDateTime.now()
                )
        );
        return saved;
    }

    public List<SalesOrder> listOrdersPendingProductionStockIn() {
        // 仅筛出状态为“待生产入库”且确实存在 DONE 生产计划的订单，
        // 避免前端看到无法入库的无效数据。
        return salesOrderRepository.findAll().stream()
                .filter(order -> STATUS_PENDING_PRODUCTION_STOCK_IN.equals(safeStatus(order.getStatus())))
                .filter(order -> !productionPlanRepository.findByPlanNoStartingWithAndStatus("PLAN-" + order.getOrderNo() + "-", "DONE").isEmpty())
                .sorted(Comparator.comparing(SalesOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    /*
     * 仓库发货。
     *
     * <p>这是履约闭环中的实物扣减节点：
     * 1. 消耗已预留/可用库存；
     * 2. 生成销售出库流水；
     * 3. 更新订单状态为已发货；
     * 4. 通知销售与客户。</p>
     */
    public SalesOrder markOrderShipped(Long orderId, String operator, String note) {
        SalesOrder order = getOrder(orderId);
        if (!STATUS_ACCEPTED.equals(safeStatus(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许仓库发货: " + order.getStatus());
        }
        User operatorUser = resolveUserByEmail(operator);
        List<StockMovement> shipments = consumeInventoryForShipment(order);
        shipments.forEach(movement -> createStockTransaction(movement.product, movement.warehouse, movement.quantity, "OUT", "SALES_ORDER", order.getId(), operatorUser, note, null));
        order.setStatus(STATUS_SHIPPED);
        SalesOrder saved = salesOrderRepository.save(order);

        notificationService.broadcast(
                "/topic/orders/sales",
                buildOrderMessage(
                        "ORDER_SHIPPED_BY_WAREHOUSE",
                        saved,
                        operator,
                        "订单 " + saved.getOrderNo() + " 已发货，请查看。",
                        saved.getShippingAddress()
                )
        );
        notificationService.broadcast(
                resolveCustomerTopic(saved),
                buildOrderMessage(
                        "ORDER_SHIPPED_TO_CUSTOMER",
                        saved,
                        operator,
                        "您的订单 " + saved.getOrderNo() + " 已发货。",
                        saved.getShippingAddress()
                )
        );
        notificationService.broadcast("/topic/orders", buildOrderMessage("ORDER_WORKFLOW_UPDATED", saved, operator));
        return saved;
    }

    private List<ProductionPlan> stockInCompletedPlans(SalesOrder order, String operator, List<ProductionPlan> completedPlans) {
        // 将已完工且质检通过的计划正式入成品库。
        // 这里同时更新 inventory_items 和 stock_transaction，体现“余额表 + 流水表”并存的库存设计。
        if (completedPlans.isEmpty()) {
            return List.of();
        }
        User operatorUser = resolveUserByEmail(operator);
        for (ProductionPlan plan : completedPlans) {
            Product product = plan.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }
            Batch batch = batchRepository.findBySourceOrderNoAndProductId(safeOrderNo(order.getOrderNo()), product.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "批次不存在，无法确认生产入库: " + product.getName()));
            if (!QualityService.STATUS_PASSED.equals(safeStatus(batch.getQualityStatus()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "批次 " + batch.getBatchNo() + " 尚未质检合格，不能入库");
            }
            double quantity = safeNumber(plan.getPlannedQuantity());
            if (quantity <= 0) {
                plan.setStatus("WAREHOUSED");
                continue;
            }
            Warehouse warehouse = resolveWarehouseForProduct(product.getId());
            InventoryItem item = findOrCreateInventoryItem(product, warehouse);
            item.setQuantity(safeNumber(item.getQuantity()) + quantity);
            item.setLot(batch.getBatchNo());
            inventoryItemRepository.save(item);

            // 每次库存变更都生成流水，是企业系统审计与追溯的基础能力。
            createStockTransaction(product, warehouse, quantity, "IN", "PRODUCTION_PLAN", plan.getId(), operatorUser, noteOrDefault("生产质检合格后仓库确认入库", operator), batch.getBatchNo());
            plan.setStatus("WAREHOUSED");
        }
        productionPlanRepository.saveAll(completedPlans);
        return completedPlans;
    }

    private void prepareCompletedPlanBatchesForQuality(SalesOrder order, List<ProductionPlan> plans) {
        // 生产完工后为每个计划创建/更新待检批次，并广播质量待办。
        for (ProductionPlan plan : plans) {
            Product product = plan.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }
            double quantity = safeNumber(plan.getPlannedQuantity());
            if (quantity <= 0) {
                continue;
            }
            Batch batch = createOrUpdateBatch(order, plan, product, quantity);
            notificationService.broadcast(
                    "/topic/quality",
                    new NotificationMessage(
                            "QUALITY_PENDING",
                            "Batch",
                            batch.getId(),
                            new QualityService.QualityNoticePayload(
                                    batch,
                                    "批次 " + batch.getBatchNo() + " 已完成生产，待质检，请查看。",
                                    product.getName() + " / 来源订单 " + safeOrderNo(batch.getSourceOrderNo())
                            ),
                            LocalDateTime.now()
                    )
            );
        }
    }

    private Batch createOrUpdateBatch(SalesOrder order, ProductionPlan plan, Product product, double quantity) {
        // 批次是“生产结果”的质量追溯载体。
        // 同一来源订单 + 产品通常只维护一个最新批次记录，便于后续质检和订单联动。
        String sourceOrderNo = order == null
                ? (isStandaloneProductionPlan(plan) ? safeOrderNo(plan.getPlanNo()) : resolveOrderNoFromPlanNo(plan.getPlanNo()))
                : safeOrderNo(order.getOrderNo());
        Batch batch = batchRepository.findBySourceOrderNoAndProductId(sourceOrderNo, product.getId())
                .orElseGet(Batch::new);
        if (batch.getBatchNo() == null || batch.getBatchNo().isBlank()) {
            batch.setBatchNo(generateBatchNo());
        }
        batch.setProduct(product);
        batch.setQuantity(quantity);
        batch.setManufactureDate(plan.getEndDate() == null ? LocalDateTime.now() : plan.getEndDate());
        batch.setSourceOrderNo(sourceOrderNo);
        batch.setQualityStatus(QualityService.STATUS_PENDING);
        batch.setQualityRemark(null);
        batch.setQualityInspectedAt(null);
        batch.setQualityInspectorId(null);
        batch.setQualityInspectorName(null);
        batch.setProductionManagerEmail(blankToNull(plan.getCompletedByEmail()));
        batch.setProductionManagerName(blankToNull(plan.getCompletedByName()));
        return batchRepository.save(batch);
    }

    private String generateBatchNo() {
        return "BT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private SalesOrder getOrder(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + orderId));
    }

    private String safeStatus(String status) {
        return status == null ? "" : status.trim();
    }

    private List<ProductShortage> evaluateShortages(SalesOrder order) {
        // 核算订单是否缺货：逐个订单明细统计需要量与当前可用量的差值。
        List<ProductShortage> shortages = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || product.getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单明细缺少产品信息");
            }
            double required = safeNumber(item.getQuantity());
            if (required <= 0) {
                continue;
            }
            double available = totalAvailableByProduct(product.getId());
            if (available + 1e-6 < required) {
                shortages.add(new ProductShortage(product.getId(), product.getName(), required, available, required - available));
            }
        }
        return shortages;
    }

    private void reserveInventoryForOrder(SalesOrder order) {
        // 锁定库存，而不是直接扣减库存。
        // 这是仓储系统里的经典做法：订单通过核查后先占用库存，真正发货时再扣减 quantity。
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }
            double remaining = safeNumber(item.getQuantity());
            List<InventoryItem> inventoryItems = new ArrayList<>(inventoryItemRepository.findByProductId(product.getId()));
            inventoryItems.sort(Comparator.comparing(InventoryItem::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            for (InventoryItem inv : inventoryItems) {
                if (remaining <= 0) {
                    break;
                }
                double available = safeNumber(inv.getQuantity()) - safeNumber(inv.getReservedQuantity());
                if (available <= 0) {
                    continue;
                }
                double allocate = Math.min(available, remaining);

                // reservedQuantity 表示“已被订单占用但尚未真实发出”的数量。
                inv.setReservedQuantity(safeNumber(inv.getReservedQuantity()) + allocate);
                remaining -= allocate;
            }
            if (remaining > 1e-6) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "库存数据变化，请重新执行仓库核查");
            }
            inventoryItemRepository.saveAll(inventoryItems);
        }
    }

    private List<ProductionPlan> createProductionPlans(SalesOrder order, List<ProductShortage> shortages, User operatorUser) {
        // 当仓库发现缺货时，自动为缺口创建生产计划。
        // 当前策略是一种“缺多少补多少”的简单拉动式生产模式。
        List<ProductionPlan> plans = new ArrayList<>();
        for (ProductShortage shortage : shortages) {
            if (shortage.getShortageQuantity() <= 0) {
                continue;
            }
            ProductionPlan plan = new ProductionPlan();
            Product product = productRepository.findById(shortage.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品不存在: " + shortage.getProductId()));
            plan.setProduct(product);
            plan.setPlanNo("PLAN-" + order.getOrderNo() + "-" + shortage.getProductId() + "-" + System.currentTimeMillis());
            plan.setPlannedQuantity(shortage.getShortageQuantity());
            plan.setStatus("PLANNED");
            plan.setStartDate(LocalDateTime.now());
            plan.setEndDate(LocalDateTime.now().plusDays(7));
            plan.setCreatedBy(operatorUser == null ? null : operatorUser.getId());
            plan.setCreatedByName(resolveDisplayName(operatorUser, null));
            plans.add(productionPlanRepository.save(plan));
        }
        return plans;
    }

    private List<StockMovement> consumeInventoryForShipment(SalesOrder order) {
        // 发货时优先消耗已预留库存，
        // 如果预留不足，再尝试从剩余可用库存继续扣减，保证发货动作尽可能成功。
        List<StockMovement> movements = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }
            double remaining = safeNumber(item.getQuantity());
            List<InventoryItem> inventoryItems = new ArrayList<>(inventoryItemRepository.findByProductId(product.getId()));
            inventoryItems.sort(Comparator.comparing(InventoryItem::getId, Comparator.nullsLast(Comparator.naturalOrder())));

            for (InventoryItem inv : inventoryItems) {
                if (remaining <= 0) {
                    break;
                }
                double reserved = Math.max(0, safeNumber(inv.getReservedQuantity()));
                if (reserved <= 0) {
                    continue;
                }

                // 第一阶段：优先消费 reservedQuantity，对应“已锁定库存发货”。
                double consume = Math.min(reserved, remaining);
                inv.setReservedQuantity(reserved - consume);
                inv.setQuantity(Math.max(0, safeNumber(inv.getQuantity()) - consume));
                remaining -= consume;
                if (consume > 1e-6) {
                    movements.add(new StockMovement(product, inv.getWarehouse(), consume));
                }
            }

            for (InventoryItem inv : inventoryItems) {
                if (remaining <= 0) {
                    break;
                }

                // 第二阶段：若预留不够，再从可用库存中补扣，增强流程容错性。
                double available = Math.max(0, safeNumber(inv.getQuantity()) - safeNumber(inv.getReservedQuantity()));
                if (available <= 0) {
                    continue;
                }
                double consume = Math.min(available, remaining);
                inv.setQuantity(Math.max(0, safeNumber(inv.getQuantity()) - consume));
                remaining -= consume;
                if (consume > 1e-6) {
                    movements.add(new StockMovement(product, inv.getWarehouse(), consume));
                }
            }

            if (remaining > 1e-6) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "库存不足，无法发货，请重新核查库存");
            }
            inventoryItemRepository.saveAll(inventoryItems);
        }
        return movements;
    }

    private InventoryItem findOrCreateInventoryItem(Product product, Warehouse warehouse) {
        // 查找指定产品在指定仓库的库存余额记录；不存在则初始化一条空记录。
        return inventoryItemRepository.findByProductId(product.getId()).stream()
                .filter(item -> item.getWarehouse() != null && item.getWarehouse().getId() != null)
                .filter(item -> item.getWarehouse().getId().equals(warehouse.getId()))
                .findFirst()
                .orElseGet(() -> {
                    InventoryItem item = new InventoryItem();
                    item.setProduct(product);
                    item.setWarehouse(warehouse);
                    item.setQuantity(0.0);
                    item.setReservedQuantity(0.0);
                    return item;
                });
    }

    private Warehouse resolveWarehouseForProduct(Long productId) {
        // 优先复用现有库存项上的仓库；如果还没有库存记录，则按产品类型选择默认仓库。
        // 这体现了“领域默认值”设计：原材料进原材料仓，成品进成品仓。
        Warehouse existing = inventoryItemRepository.findByProductId(productId).stream()
                .map(InventoryItem::getWarehouse)
                .filter(warehouse -> warehouse != null && warehouse.getId() != null)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        Product product = productRepository.findById(productId).orElse(null);
        String defaultWarehouseCode = WarehouseDefaults.defaultWarehouseCodeFor(product);
        return warehouseRepository.findByCodeIgnoreCase(defaultWarehouseCode)
                .or(() -> warehouseRepository.findAll().stream()
                        .sorted(Comparator.comparing(Warehouse::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "未配置仓库，无法完成库存入库"));
    }

    private void createStockTransaction(Product product,
                                        Warehouse warehouse,
                                        double quantity,
                                        String type,
                                        String relatedType,
                                        Long relatedId,
                                        Long createdBy) {
        createStockTransaction(product, warehouse, quantity, type, relatedType, relatedId, createdBy, null, null, null);
    }

    private void createStockTransaction(Product product,
                                        Warehouse warehouse,
                                        double quantity,
                                        String type,
                                        String relatedType,
                                        Long relatedId,
                                        Long createdBy,
                                        String lot) {
        createStockTransaction(product, warehouse, quantity, type, relatedType, relatedId, createdBy, null, null, lot);
    }

    private void createStockTransaction(Product product,
                                        Warehouse warehouse,
                                        double quantity,
                                        String type,
                                        String relatedType,
                                        Long relatedId,
                                        User operatorUser,
                                        String remark,
                                        String lot) {
        createStockTransaction(product, warehouse, quantity, type, relatedType, relatedId,
                operatorUser == null ? null : operatorUser.getId(),
                operatorUser == null ? null : resolveDisplayName(operatorUser, null),
                remark,
                lot);
    }

    private void createStockTransaction(Product product,
                                        Warehouse warehouse,
                                        double quantity,
                                        String type,
                                        String relatedType,
                                        Long relatedId,
                                        Long createdBy,
                                        String createdByName,
                                        String remark,
                                        String lot) {
        // 统一的库存流水创建入口。
        // 使用多层重载方法，是为了兼顾不同调用场景：
        // 有的只知道 createdBy，有的拿到了完整 operatorUser，有的需要 lot / remark，有的不需要。
        StockTransaction tx = new StockTransaction();
        tx.setTransactionNo("ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        tx.setProduct(product);
        tx.setWarehouse(warehouse);
        tx.setChangeQuantity(quantity);
        tx.setTransactionType(type);
        tx.setLot(blankToNull(lot));
        tx.setRelatedType(relatedType);
        tx.setRelatedId(relatedId);
        tx.setCreatedBy(createdBy);
        tx.setCreatedByName(blankToNull(createdByName));
        tx.setRemark(blankToNull(remark));
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(tx);
    }

    private String noteOrDefault(String fallback, String operator) {
        return blankToNull(fallback) == null ? blankToNull(operator) : fallback;
    }

    private double totalAvailableByProduct(Long productId) {
        // 可用库存 = quantity - reservedQuantity。
        // 这是仓储履约判断中的核心指标，比单纯 quantity 更准确。
        return inventoryItemRepository.findByProductId(productId).stream()
                .mapToDouble(item -> Math.max(0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity())))
                .sum();
    }

    private ProductionPlan getProductionPlan(Long planId) {
        if (planId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生产计划ID不能为空");
        }
        return productionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产计划不存在: " + planId));
    }

    private void requireInventoryAlertPlan(ProductionPlan plan) {
        if (!isInventoryAlertPlan(plan)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该生产计划不是库存预警补产计划");
        }
    }

    private void requireStandaloneProductionPlan(ProductionPlan plan) {
        if (!isStandaloneProductionPlan(plan)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该生产计划仍需按订单生产流程执行");
        }
    }

    private boolean isInventoryAlertPlan(ProductionPlan plan) {
        return plan != null && plan.getPlanNo() != null && plan.getPlanNo().startsWith("PLAN-ALERT-");
    }

    private boolean isManualProductionPlan(ProductionPlan plan) {
        return plan != null && plan.getPlanNo() != null && plan.getPlanNo().startsWith("PLAN-MANUAL-");
    }

    private boolean isStandaloneProductionPlan(ProductionPlan plan) {
        return isInventoryAlertPlan(plan) || isManualProductionPlan(plan);
    }

    private boolean hasPassedQualityBatchForStandalonePlan(ProductionPlan plan) {
        Product product = plan == null ? null : plan.getProduct();
        if (product == null || product.getId() == null) {
            return false;
        }
        return batchRepository.findBySourceOrderNoAndProductId(plan.getPlanNo(), product.getId())
                .map(batch -> QualityService.STATUS_PASSED.equals(safeStatus(batch.getQualityStatus())))
                .orElse(false);
    }

    private String resolveOrderNoFromPlanNo(String planNo) {
        if (planNo == null || !planNo.startsWith("PLAN-")) {
            return "";
        }
        if (planNo.startsWith("PLAN-ALERT-")) {
            return planNo;
        }
        if (planNo.startsWith("PLAN-MANUAL-")) {
            return planNo;
        }
        String remaining = planNo.substring("PLAN-".length());
        int lastSeparator = remaining.lastIndexOf('-');
        if (lastSeparator <= 0) {
            return remaining;
        }
        String withoutTimestamp = remaining.substring(0, lastSeparator);
        int productSeparator = withoutTimestamp.lastIndexOf('-');
        if (productSeparator <= 0) {
            return withoutTimestamp;
        }
        return withoutTimestamp.substring(0, productSeparator);
    }

    private String safeOrderNo(String orderNo) {
        return orderNo == null || orderNo.isBlank() ? "-" : orderNo;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private User resolveUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
    }

    private String resolveDisplayName(User user, String fallback) {
        if (user != null) {
            String name = user.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return blankToNull(fallback);
    }

    private NotificationMessage buildOrderMessage(String type, SalesOrder order, String operator) {
        WorkflowEvent event = new WorkflowEvent(order, List.of(), List.of(), operator, "", "", "");
        return new NotificationMessage(type, "SalesOrder", order.getId(), event, LocalDateTime.now());
    }

    private NotificationMessage buildOrderMessage(String type,
                                                  SalesOrder order,
                                                  String operator,
                                                  String notificationTitle,
                                                  String notificationMeta) {
        WorkflowEvent event = new WorkflowEvent(order, List.of(), List.of(), operator, "", notificationTitle, notificationMeta);
        return new NotificationMessage(type, "SalesOrder", order.getId(), event, LocalDateTime.now());
    }

    private NotificationMessage buildReviewMessage(String type,
                                                   SalesOrder order,
                                                   List<ProductShortage> shortages,
                                                   List<ProductionPlan> plans,
                                                   String operator,
                                                   String note) {
        WorkflowEvent event = new WorkflowEvent(order, shortages, plans, operator, note, "", "");
        return new NotificationMessage(type, "SalesOrder", order.getId(), event, LocalDateTime.now());
    }

    private NotificationMessage buildReviewMessage(String type,
                                                   SalesOrder order,
                                                   List<ProductShortage> shortages,
                                                   List<ProductionPlan> plans,
                                                   String operator,
                                                   String note,
                                                   String notificationTitle,
                                                   String notificationMeta) {
        WorkflowEvent event = new WorkflowEvent(order, shortages, plans, operator, note, notificationTitle, notificationMeta);
        return new NotificationMessage(type, "SalesOrder", order.getId(), event, LocalDateTime.now());
    }

    private String summarizePlanNos(List<ProductionPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return "-";
        }
        List<String> planNos = plans.stream()
                .map(ProductionPlan::getPlanNo)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        if (planNos.isEmpty()) {
            return "-";
        }
        if (planNos.size() == 1) {
            return planNos.get(0);
        }
        return planNos.get(0) + " 等" + planNos.size() + "条";
    }

    private String resolveCustomerTopic(SalesOrder order) {
        if (order == null || order.getCustomer() == null || order.getCustomer().getEmail() == null) {
            return "/topic/orders/customer";
        }
        String normalized = order.getCustomer().getEmail().trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "/topic/orders/customer" : "/topic/orders/customer/" + normalized;
    }

    private double safeNumber(Double value) {
        return value == null ? 0.0 : value;
    }

    private static class StockMovement {
        private final Product product;
        private final Warehouse warehouse;
        private final double quantity;

        private StockMovement(Product product, Warehouse warehouse, double quantity) {
            this.product = product;
            this.warehouse = warehouse;
            this.quantity = quantity;
        }
    }

    public static class WarehouseReviewResult {
        private final SalesOrder order;
        private final List<ProductShortage> shortages;
        private final List<ProductionPlan> productionPlans;
        private final String note;

        public WarehouseReviewResult(SalesOrder order,
                                     List<ProductShortage> shortages,
                                     List<ProductionPlan> productionPlans,
                                     String note) {
            this.order = order;
            this.shortages = shortages;
            this.productionPlans = productionPlans;
            this.note = note;
        }

        public SalesOrder getOrder() {
            return order;
        }

        public List<ProductShortage> getShortages() {
            return shortages;
        }

        public List<ProductionPlan> getProductionPlans() {
            return productionPlans;
        }

        public String getNote() {
            return note;
        }
    }

    public static class ProductShortage {
        private final Long productId;
        private final String productName;
        private final Double requiredQuantity;
        private final Double availableQuantity;
        private final Double shortageQuantity;

        public ProductShortage(Long productId,
                               String productName,
                               Double requiredQuantity,
                               Double availableQuantity,
                               Double shortageQuantity) {
            this.productId = productId;
            this.productName = productName;
            this.requiredQuantity = requiredQuantity;
            this.availableQuantity = availableQuantity;
            this.shortageQuantity = shortageQuantity;
        }

        public Long getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public Double getRequiredQuantity() {
            return requiredQuantity;
        }

        public Double getAvailableQuantity() {
            return availableQuantity;
        }

        public Double getShortageQuantity() {
            return shortageQuantity;
        }
    }

    public static class WorkflowEvent {
        private final SalesOrder order;
        private final List<ProductShortage> shortages;
        private final List<ProductionPlan> productionPlans;
        private final String operator;
        private final String note;
        private final String notificationTitle;
        private final String notificationMeta;

        public WorkflowEvent(SalesOrder order,
                             List<ProductShortage> shortages,
                             List<ProductionPlan> productionPlans,
                             String operator,
                             String note,
                             String notificationTitle,
                             String notificationMeta) {
            this.order = order;
            this.shortages = shortages;
            this.productionPlans = productionPlans;
            this.operator = operator;
            this.note = note;
            this.notificationTitle = notificationTitle;
            this.notificationMeta = notificationMeta;
        }

        public SalesOrder getOrder() {
            return order;
        }

        public List<ProductShortage> getShortages() {
            return shortages;
        }

        public List<ProductionPlan> getProductionPlans() {
            return productionPlans;
        }

        public String getOperator() {
            return operator;
        }

        public String getNote() {
            return note;
        }

        public String getNotificationTitle() {
            return notificationTitle;
        }

        public String getNotificationMeta() {
            return notificationMeta;
        }
    }
}

