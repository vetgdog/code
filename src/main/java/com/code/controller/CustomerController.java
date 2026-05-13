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

/**
 * 客户门户控制器。
 *
 * <p>该控制器对外暴露的是“客户自助下单 + 查看我的订单 + 质量追溯”三条主路径。它与后台销售端最大的区别在于：
 * 所有读取与写入都必须绑定当前登录客户，避免客户看到其他客户的数据。</p>
 *
 * <p>从实现上看，这里既做了客户身份解析，也承担了门户通知发送职责。下单成功后会同时通知客户自己与销售团队，
 * 因而它不仅是 CRUD 入口，也是订单协同的触发点。</p>
 */
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    /**
     * 订单仓库，负责客户订单主表/明细的持久化与查询。
     */
    @Autowired
    private SalesOrderRepository salesOrderRepository;

    /**
     * 客户档案仓库，用于把当前登录邮箱映射成客户主体。
     */
    @Autowired
    private CustomerRepository customerRepository;

    /**
     * 用户仓库，用于补齐订单 createdBy 等审计字段，并兼容历史账号角色判断。
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * 产品主数据仓库，客户下单时的价格与产品存在性都以这里为准。
     */
    @Autowired
    private ProductRepository productRepository;

    /**
     * 批次仓库，用于按订单号追溯生产批次。
     */
    @Autowired
    private BatchRepository batchRepository;

    /**
     * 质检记录仓库，用于拼装客户可读的质量追溯视图。
     */
    @Autowired
    private QualityRecordRepository qualityRecordRepository;

    /**
     * WebSocket 推送模板，用于下单成功后的实时通知广播。
     */
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 兼容旧前端的客户订单列表接口，按客户 id 直接查询。
     *
     * <p>该接口更像历史兼容保留口，真正面向当前门户的入口是 `/me/orders`。</p>
     */
    @GetMapping("/{customerId}/orders")
    public List<SalesOrder> listOrders(@PathVariable Long customerId) {
        // 这是一个历史兼容接口：直接按客户 id 查，不依赖当前登录身份。
        // 新门户更推荐 `/me/orders`，因为它天然绑定当前客户上下文。
        return sortByLatestTime(salesOrderRepository.findByCustomerId(customerId));
    }

    /**
     * 返回当前登录客户自己的订单列表。
     */
    @GetMapping("/me/orders")
    public List<SalesOrder> listMyOrders(Authentication authentication) {
        Customer customer = findCurrentCustomer(authentication);
        if (customer == null) {

            // 这里返回空列表而不是 401/403，说明该接口偏向“门户页面初始化友好”，
            // 便于前端在未拿到客户上下文时平稳展示空态。
            return List.of();
        }
        return sortByLatestTime(salesOrderRepository.findByCustomerId(customer.getId()));
    }

    /**
     * 客户提交订单。
     *
     * <p>处理逻辑采用典型门户入口模式：先校验客户身份与基础字段，再根据产品主数据回填单价，最后生成订单明细与总额。
     * 注意这里不会信任前端传入的单价，而是统一从产品表读取，这能避免门户被篡改价格。</p>
     *
     * <p>订单保存后会立即推送两类消息：</p>
     * <ul>
     *   <li>客户主题：反馈“提交成功”</li>
     *   <li>销售主题：提醒“有新订单待审核”</li>
     * </ul>
     */
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CustomerOrderRequest request, Authentication authentication) {
        Customer customer = requireCurrentCustomer(authentication);

        // 控制器先做最基本的表单级校验，把明显错误尽早拦住，
        // 避免进入订单构建、查产品、推送通知等后续分支。
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

        // createdBy 使用当前登录系统账号 id，而 customer 字段保存业务客户主体，
        // 两者分离后，既能追溯“谁登录提交的”，又能保留“这是谁的订单”。
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

            // 单价始终以后端主数据为准，不信任前端传入的 unitPrice，
            // 这是门户下单场景里最核心的防篡改措施之一。
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

        // 一次下单触发两类通知：
        // 1) 发给客户本人，确认系统已受理；
        // 2) 发给销售团队，提示后续审核动作。
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

    /**
     * 查询单个订单详情，并强制校验该订单属于当前客户。
     */
    @GetMapping("/orders/{orderId}")
    public SalesOrder getOrder(@PathVariable Long orderId, Authentication authentication) {
        Customer customer = requireCurrentCustomer(authentication);
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));

        // 即使订单存在，也必须再次校验归属关系，
        // 防止客户通过猜测/遍历订单 id 越权查看他人订单详情。
        if (order.getCustomer() == null || !customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该订单");
        }
        return order;
    }

    /**
     * 客户侧质量追溯。
     *
     * <p>入口参数是订单号而不是批次号，符合客户视角：客户通常只知道自己订单，不知道内部批次编号。控制器会把订单
     * 关联到批次，再进一步拉取对应质检记录，最终拼出可直接展示的追溯视图。</p>
     */
    @GetMapping("/quality-trace")
    public QualityTraceView traceQuality(@org.springframework.web.bind.annotation.RequestParam String orderNo,
                                         Authentication authentication) {
        Customer customer = requireCurrentCustomer(authentication);
        if (isBlank(orderNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入订单号");
        }

        // 当前实现通过遍历全部订单按单号匹配，逻辑简单但不是最优查询路径；
        // 若追溯访问量增大，可补 repository 按 orderNo 精准查询。
        SalesOrder order = salesOrderRepository.findAll().stream()
                .filter(item -> item.getOrderNo() != null && item.getOrderNo().equalsIgnoreCase(orderNo.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getCustomer() == null || !customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查询该订单的质量信息");
        }

        // 客户最终看到的是聚合视图而不是分散的批次表/质检表，
        // 因此这里会把“订单 -> 批次 -> 质检记录”三层关系拼装成一次返回结果。
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

    /**
     * 严格要求当前登录人必须具备客户下单资格。
     *
     * <p>这里兼容了旧数据：如果账号没有 `ROLE_CUSTOMER`，但存在客户档案且账号本身也没有其他业务角色，
     * 仍允许继续作为客户使用，避免历史账号因为角色清洗不彻底而无法下单。</p>
     */
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

        // 这里保留“只有客户档案、没有显式客户角色”的旧账号兼容逻辑；
        // 但如果该账号已经拥有其他业务角色，则不再把它视作纯客户账号，避免角色边界混乱。
        if (!hasCustomerRole) {
            User user = userRepository.findByEmail(authentication.getName().toLowerCase(Locale.ROOT)).orElse(null);
            if (user != null && user.getRoles() != null && !user.getRoles().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有客户下单权限，请重新登录客户账号后重试");
            }
        }
        return customer;
    }

    /**
     * 通过登录邮箱反查客户档案。
     */
    private Customer findCurrentCustomer(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        // 以邮箱为客户门户的统一身份锚点，保证登录体系与客户档案通过 email 对齐。
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        return customerRepository.findByEmail(email).orElse(null);
    }

    /**
     * 统一按最近业务时间倒序，符合客户查看“最新订单优先”的直觉。
     */
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

    /**
     * 为客户构造专属订阅主题。
     *
     * <p>主题名基于邮箱归一化生成，既便于前端按用户订阅，也避免原始邮箱中的特殊字符破坏 topic 路径。</p>
     */
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

    /**
     * 客户门户通知载荷。
     *
     * <p>除原始订单对象外，还额外提供标题与辅助信息，方便前端统一渲染 toast、消息卡片或站内信。</p>
     */
    public static class NoticePayload {
        private final SalesOrder order;
        private final String notificationTitle;
        private final String notificationMeta;

        /**
         * 通知载荷同时带上原始订单与摘要文本，方便前端在不同通知组件中复用。
         */
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

    /**
     * 客户提交订单的请求体。
     *
     * <p>它刻意只保留客户可填写字段，不暴露状态、总额、创建时间等服务端控制字段，避免接口被客户端反向驱动。</p>
     */
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

    /**
     * 客户订单明细请求体。
     *
     * <p>`unitPrice` 当前未参与后端定价计算，保留该字段更像是兼容前端模型或未来做价格预览扩展。</p>
     */
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

    /**
     * 客户质量追溯聚合视图。
     *
     * <p>该对象面向客户门户展示，强调“订单维度”的追溯体验，而不是暴露内部批次表结构。</p>
     */
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

    /**
     * 单个批次的质量视图，聚合了批次基础信息与检验记录历史。
     *
     * <p>客户通常既关心这批货是否合格，也关心具体检验记录，因此这里同时返回批次摘要和 records 明细。</p>
     */
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

