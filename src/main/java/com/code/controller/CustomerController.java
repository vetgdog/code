package com.code.controller;

import com.code.entity.Customer;
import com.code.entity.OrderItem;
import com.code.entity.Product;
import com.code.entity.SalesOrder;
import com.code.entity.User;
import com.code.repository.CustomerRepository;
import com.code.repository.ProductRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private SimpMessagingTemplate messagingTemplate;

    // portal: list orders for a specific customer id (kept for compatibility)
    @GetMapping("/{customerId}/orders")
    public List<SalesOrder> listOrders(@PathVariable Long customerId) {
        return sortByBusinessStatus(salesOrderRepository.findByCustomerId(customerId));
    }

    @GetMapping("/me/orders")
    public List<SalesOrder> listMyOrders(Authentication authentication) {
        Customer customer = findCurrentCustomer(authentication);
        if (customer == null) {
            return List.of();
        }
        return sortByBusinessStatus(salesOrderRepository.findByCustomerId(customer.getId()));
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
        order.setStatus("已接单");
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
        messagingTemplate.convertAndSend("/topic/orders", saved);
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

    private Customer requireCurrentCustomer(Authentication authentication) {
        Customer customer = findCurrentCustomer(authentication);
        if (customer == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前账号未绑定客户档案，请联系管理员");
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

    private List<SalesOrder> sortByBusinessStatus(List<SalesOrder> source) {
        return source.stream()
                .sorted(Comparator
                        .comparingInt((SalesOrder order) -> statusRank(order.getStatus()))
                        .thenComparing(SalesOrder::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SalesOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private int statusRank(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("已接单".equals(status) || "NEW".equals(value) || "ACCEPTED".equals(value)) {
            return 1;
        }
        if ("生产中".equals(status) || "IN_PRODUCTION".equals(value) || "PRODUCING".equals(value)) {
            return 2;
        }
        if ("已发货".equals(status) || "SHIPPED".equals(value)) {
            return 3;
        }
        if ("已完成".equals(status) || "DONE".equals(value) || "COMPLETED".equals(value)) {
            return 4;
        }
        return 99;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String generateOrderNo() {
        return "SO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
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
}

