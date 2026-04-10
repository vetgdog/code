package com.code.service;

import com.code.entity.InventoryItem;
import com.code.entity.OrderItem;
import com.code.entity.ProductionPlan;
import com.code.entity.Product;
import com.code.entity.SalesOrder;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesOrderRepository;
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

@Service
public class OrderWorkflowService {

    public static final String STATUS_PENDING_SALES_REVIEW = "待销售审核";
    public static final String STATUS_PENDING_WAREHOUSE_CHECK = "待仓库核查";
    public static final String STATUS_ACCEPTED = "已接单";
    public static final String STATUS_IN_PRODUCTION = "生产中";

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public SalesOrder routeToWarehouseCheck(Long orderId, String operator) {
        SalesOrder order = getOrder(orderId);
        String currentStatus = safeStatus(order.getStatus());
        if (!STATUS_PENDING_SALES_REVIEW.equals(currentStatus) && !"NEW".equals(currentStatus.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不允许提交仓库核查: " + order.getStatus());
        }

        order.setStatus(STATUS_PENDING_WAREHOUSE_CHECK);
        order.setCreatedAt(order.getCreatedAt() == null ? LocalDateTime.now() : order.getCreatedAt());
        SalesOrder saved = salesOrderRepository.save(order);

        notificationService.broadcast("/topic/orders", buildOrderMessage("ORDER_TO_WAREHOUSE", saved, operator));
        notificationService.broadcast("/topic/orders/warehouse", buildOrderMessage("WAREHOUSE_ACTION_REQUIRED", saved, operator));
        notificationService.broadcast("/topic/orders/sales", buildOrderMessage("ORDER_SALES_ROUTED", saved, operator));

        return saved;
    }

    @Transactional
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

        if (shortages.isEmpty()) {
            reserveInventoryForOrder(order);
            order.setStatus(STATUS_ACCEPTED);
            notificationService.broadcast("/topic/orders/warehouse", buildOrderMessage("ORDER_STOCK_CONFIRMED", order, operator));
            notificationService.broadcast("/topic/orders/sales", buildOrderMessage("ORDER_READY_FOR_FULFILLMENT", order, operator));
        } else {
            order.setStatus(STATUS_IN_PRODUCTION);
            createdPlans = createProductionPlans(order, shortages);
            notificationService.broadcast("/topic/orders/production", buildReviewMessage("ORDER_PRODUCTION_REQUIRED", order, shortages, createdPlans, operator, note));
            notificationService.broadcast("/topic/production", buildReviewMessage("ORDER_PRODUCTION_REQUIRED", order, shortages, createdPlans, operator, note));
            notificationService.broadcast("/topic/orders/warehouse", buildReviewMessage("ORDER_STOCK_SHORTAGE", order, shortages, createdPlans, operator, note));
            notificationService.broadcast("/topic/orders/sales", buildReviewMessage("ORDER_STOCK_SHORTAGE", order, shortages, createdPlans, operator, note));
        }

        SalesOrder savedOrder = salesOrderRepository.save(order);
        WarehouseReviewResult result = new WarehouseReviewResult(savedOrder, shortages, createdPlans, note);
        notificationService.broadcast("/topic/orders", new NotificationMessage("ORDER_WORKFLOW_UPDATED", "SalesOrder", savedOrder.getId(), result, LocalDateTime.now()));
        return result;
    }

    private SalesOrder getOrder(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + orderId));
    }

    private String safeStatus(String status) {
        return status == null ? "" : status.trim();
    }

    private List<ProductShortage> evaluateShortages(SalesOrder order) {
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
                inv.setReservedQuantity(safeNumber(inv.getReservedQuantity()) + allocate);
                remaining -= allocate;
            }
            if (remaining > 1e-6) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "库存数据变化，请重新执行仓库核查");
            }
            inventoryItemRepository.saveAll(inventoryItems);
        }
    }

    private List<ProductionPlan> createProductionPlans(SalesOrder order, List<ProductShortage> shortages) {
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
            plan.setCreatedBy(order.getCreatedBy());
            plans.add(productionPlanRepository.save(plan));
        }
        return plans;
    }

    private double totalAvailableByProduct(Long productId) {
        return inventoryItemRepository.findByProductId(productId).stream()
                .mapToDouble(item -> Math.max(0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity())))
                .sum();
    }

    private NotificationMessage buildOrderMessage(String type, SalesOrder order, String operator) {
        WorkflowEvent event = new WorkflowEvent(order, List.of(), List.of(), operator, "");
        return new NotificationMessage(type, "SalesOrder", order.getId(), event, LocalDateTime.now());
    }

    private NotificationMessage buildReviewMessage(String type,
                                                   SalesOrder order,
                                                   List<ProductShortage> shortages,
                                                   List<ProductionPlan> plans,
                                                   String operator,
                                                   String note) {
        WorkflowEvent event = new WorkflowEvent(order, shortages, plans, operator, note);
        return new NotificationMessage(type, "SalesOrder", order.getId(), event, LocalDateTime.now());
    }

    private double safeNumber(Double value) {
        return value == null ? 0.0 : value;
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

        public WorkflowEvent(SalesOrder order,
                             List<ProductShortage> shortages,
                             List<ProductionPlan> productionPlans,
                             String operator,
                             String note) {
            this.order = order;
            this.shortages = shortages;
            this.productionPlans = productionPlans;
            this.operator = operator;
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

        public String getOperator() {
            return operator;
        }

        public String getNote() {
            return note;
        }
    }
}

