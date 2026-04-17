package com.code.controller;

import com.code.entity.Customer;
import com.code.entity.OrderItem;
import com.code.entity.Product;
import com.code.entity.QualityRecord;
import com.code.entity.SalesOrder;
import com.code.entity.User;
import com.code.repository.BatchRepository;
import com.code.repository.CustomerRepository;
import com.code.repository.ProductRepository;
import com.code.repository.QualityRecordRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.UserRepository;
import com.code.service.OrderWorkflowService;
import com.code.websocket.NotificationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private QualityRecordRepository qualityRecordRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // portal: list orders for a specific customer id (kept for compatibility)
    @GetMapping("/{customerId}/orders")
    public List<SalesOrder> listOrders(@PathVariable Long customerId) {
        return sortByLatestTime(salesOrderRepository.findByCustomerId(customerId));
    }

    @GetMapping("/me/orders")
    public List<SalesOrder> listMyOrders(Authentication authentication) {
        Customer customer = findCurrentCustomer(authentication);
        if (customer == null) {
            return List.of();
        }
        return sortByLatestTime(salesOrderRepository.findByCustomerId(customer.getId()));
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CustomerOrderRequest request, Authentication authentication) {
        Customer customer = requireCurrentCustomer(authentication);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body("请至少提交一条订单明细");
        }
        if (isBlank(request.getShippingAddress())) {
            return ResponseEntity.badRequest().body("请填写收货地址");
        }

        SalesOrder order = new SalesOrder();
        order.setOrderNo(isBlank(request.getOrderNo()) ? generateOrderNo() : request.getOrderNo().trim());
        order.setCustomer(customer);
        order.setStatus(OrderWorkflowService.STATUS_PENDING_SALES_REVIEW);
        order.setShippingAddress(request.getShippingAddress().trim());
        order.setOrderDate(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());

        User user = userRepository.findByEmail(authentication.getName().toLowerCase(Locale.ROOT)).orElse(null);
        order.setCreatedBy(user == null ? null : user.getId());

        double total = 0.0;
        List<OrderItem> items = new ArrayList<>();
        for (CustomerOrderItemRequest itemRequest : request.getItems()) {
            if (itemRequest == null || itemRequest.getProductId() == null) {
                return ResponseEntity.badRequest().body("请选择产品");
            }
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body("产品数量必须大于0");
            }

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品不存在: " + itemRequest.getProductId()));
            double qty = itemRequest.getQuantity();
            double price = product.getUnitPrice() == null ? 0.0 : product.getUnitPrice();
            double lineTotal = qty * price;

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(qty);
            item.setUnitPrice(price);
            item.setLineTotal(lineTotal);
            item.setSalesOrder(order);
            items.add(item);
            total += lineTotal;
        }
        order.setItems(items);
        order.setTotalAmount(total);

        SalesOrder saved = salesOrderRepository.save(order);
        NotificationMessage customerMessage = new NotificationMessage(
                "ORDER_SUBMITTED",
                "SalesOrder",
                saved.getId(),
                new NoticePayload(saved, "您的订单 " + saved.getOrderNo() + " 已提交成功，请耐心等候。", saved.getShippingAddress()),
                LocalDateTime.now()
        );
        NotificationMessage salesMessage = new NotificationMessage(
                "ORDER_SUBMITTED",
                "SalesOrder",
                saved.getId(),
                new NoticePayload(saved, "接收一条新的客户订单 " + saved.getOrderNo() + "，请审核。", saved.getShippingAddress()),
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend(resolveCustomerTopic(saved), customerMessage);
        messagingTemplate.convertAndSend("/topic/orders", salesMessage);
        messagingTemplate.convertAndSend("/topic/orders/sales", salesMessage);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/orders/{orderId}")
    public SalesOrder getOrder(@PathVariable Long orderId, Authentication authentication) {
        Customer customer = requireCurrentCustomer(authentication);
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getCustomer() == null || !customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该订单");
        }
        return order;
    }

    @GetMapping("/quality-trace")
    public QualityTraceView traceQuality(@org.springframework.web.bind.annotation.RequestParam String orderNo,
                                         Authentication authentication) {
        Customer customer = requireCurrentCustomer(authentication);
        if (isBlank(orderNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入订单号");
        }
        SalesOrder order = salesOrderRepository.findAll().stream()
                .filter(item -> item.getOrderNo() != null && item.getOrderNo().equalsIgnoreCase(orderNo.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getCustomer() == null || !customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查询该订单的质量信息");
        }
        List<BatchQualityView> batches = batchRepository.findBySourceOrderNoIgnoreCaseOrderByCreatedAtDesc(order.getOrderNo()).stream()
                .map(batch -> new BatchQualityView(
                        batch.getId(),
                        batch.getBatchNo(),
                        batch.getProduct() == null ? null : batch.getProduct().getSku(),
                        batch.getProduct() == null ? null : batch.getProduct().getName(),
                        batch.getQuantity(),
                        batch.getQualityStatus(),
                        batch.getQualityRemark(),
                        batch.getQualityInspectorName(),
                        batch.getQualityInspectedAt(),
                        qualityRecordRepository.findByBatchIdOrderByInspectionDateDesc(batch.getId())
                ))
                .collect(Collectors.toList());
        return new QualityTraceView(order.getId(), order.getOrderNo(), order.getStatus(), batches);
    }

    private Customer requireCurrentCustomer(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后再下单");
        }
        Customer customer = findCurrentCustomer(authentication);
        if (customer == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有客户下单权限，请重新登录客户账号后重试");
        }
        boolean hasCustomerRole = authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .anyMatch("ROLE_CUSTOMER"::equals);
        // Keep compatible with old accounts that only have customer profile records.
        if (!hasCustomerRole) {
            User user = userRepository.findByEmail(authentication.getName().toLowerCase(Locale.ROOT)).orElse(null);
            if (user != null && user.getRoles() != null && !user.getRoles().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有客户下单权限，请重新登录客户账号后重试");
            }
        }
        return customer;
    }

    private Customer findCurrentCustomer(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        return customerRepository.findByEmail(email).orElse(null);
    }

    private List<SalesOrder> sortByLatestTime(List<SalesOrder> source) {
        return source.stream()
                .sorted(Comparator
                        .comparing(SalesOrder::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SalesOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SalesOrder::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }


    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String generateOrderNo() {
        return "SO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
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

    public static class NoticePayload {
        private final SalesOrder order;
        private final String notificationTitle;
        private final String notificationMeta;

        public NoticePayload(SalesOrder order, String notificationTitle, String notificationMeta) {
            this.order = order;
            this.notificationTitle = notificationTitle;
            this.notificationMeta = notificationMeta;
        }

        public SalesOrder getOrder() {
            return order;
        }

        public String getNotificationTitle() {
            return notificationTitle;
        }

        public String getNotificationMeta() {
            return notificationMeta;
        }
    }

    public static class CustomerOrderRequest {
        private String orderNo;
        private String shippingAddress;
        private List<CustomerOrderItemRequest> items;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public List<CustomerOrderItemRequest> getItems() {
            return items;
        }

        public void setItems(List<CustomerOrderItemRequest> items) {
            this.items = items;
        }

        public String getShippingAddress() {
            return shippingAddress;
        }

        public void setShippingAddress(String shippingAddress) {
            this.shippingAddress = shippingAddress;
        }
    }

    public static class CustomerOrderItemRequest {
        private Long productId;
        private Double quantity;
        private Double unitPrice;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Double getQuantity() {
            return quantity;
        }

        public void setQuantity(Double quantity) {
            this.quantity = quantity;
        }

        public Double getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(Double unitPrice) {
            this.unitPrice = unitPrice;
        }
    }

    public static class QualityTraceView {
        private final Long orderId;
        private final String orderNo;
        private final String status;
        private final List<BatchQualityView> batches;

        public QualityTraceView(Long orderId, String orderNo, String status, List<BatchQualityView> batches) {
            this.orderId = orderId;
            this.orderNo = orderNo;
            this.status = status;
            this.batches = batches;
        }

        public Long getOrderId() {
            return orderId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getStatus() {
            return status;
        }

        public List<BatchQualityView> getBatches() {
            return batches;
        }
    }

    public static class BatchQualityView {
        private final Long id;
        private final String batchNo;
        private final String productSku;
        private final String productName;
        private final Double quantity;
        private final String qualityStatus;
        private final String qualityRemark;
        private final String inspectorName;
        private final LocalDateTime inspectedAt;
        private final List<QualityRecord> records;

        public BatchQualityView(Long id,
                                String batchNo,
                                String productSku,
                                String productName,
                                Double quantity,
                                String qualityStatus,
                                String qualityRemark,
                                String inspectorName,
                                LocalDateTime inspectedAt,
                                List<QualityRecord> records) {
            this.id = id;
            this.batchNo = batchNo;
            this.productSku = productSku;
            this.productName = productName;
            this.quantity = quantity;
            this.qualityStatus = qualityStatus;
            this.qualityRemark = qualityRemark;
            this.inspectorName = inspectorName;
            this.inspectedAt = inspectedAt;
            this.records = records;
        }

        public Long getId() { return id; }
        public String getBatchNo() { return batchNo; }
        public String getProductSku() { return productSku; }
        public String getProductName() { return productName; }
        public Double getQuantity() { return quantity; }
        public String getQualityStatus() { return qualityStatus; }
        public String getQualityRemark() { return qualityRemark; }
        public String getInspectorName() { return inspectorName; }
        public LocalDateTime getInspectedAt() { return inspectedAt; }
        public List<QualityRecord> getRecords() { return records; }
    }
}

