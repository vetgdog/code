package com.code.controller;

import com.code.entity.OrderItem;
import com.code.entity.SalesRecord;
import com.code.entity.SalesOrder;
import com.code.util.CsvExportUtils;
import com.code.repository.SalesOrderRepository;
import com.code.entity.ProductionPlan;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesRecordRepository;
import com.code.repository.UserRepository;
import com.code.service.OrderWorkflowService;
import com.code.websocket.NotificationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@RestController
@RequestMapping("/api/v1/orders")
/*
 * 销售订单控制器。
 *
 * <p>该类是订单主流程的 API 边界层，负责承接销售、仓库、生产、质检等多个角色围绕订单进行的协同操作。
 * 它不只是“订单增删改查”，更像一个工作流编排入口：
 * 从订单创建、销售审核、仓库核查、生产完工、质检联动、仓库发货，到销售记录生成，
 * 各个状态流转都通过这里的接口暴露给前端。</p>
 *
 * <p>在架构上，Controller 负责：接收 HTTP 请求、做基础权限校验、调用 Service 执行业务、返回结果。
 * 真正复杂的库存、批次、状态机逻辑被下沉到 OrderWorkflowService 中，体现了典型的“控制层薄、服务层厚”的企业级分层设计。</p>
 */
public class SalesOrderController {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderWorkflowService orderWorkflowService;

    @GetMapping
    /*
     * 查询全部销售订单。
     *
     * <p>这里按订单时间、创建时间、主键倒序排序，目标是让最新业务单据优先展示给前端。
     * 当前实现直接 findAll 后在内存排序，适合中小规模数据；当订单量增大时，
     * 更推荐把排序逻辑下推到数据库层，减少内存压力和无效数据传输。</p>
     */
    public List<SalesOrder> list() {
        return salesOrderRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(SalesOrder::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SalesOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SalesOrder::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @PostMapping
    /*
     * 创建销售订单。
     *
     * <p>该方法的核心职责是把前端提交的订单主表 + 明细数据整理成可持久化的领域对象，
     * 并在保存前计算订单总金额与每行明细金额。</p>
     *
     * <p>这里采用“后端统一计算金额”而不是完全信任前端传值，
     * 是企业系统里很重要的设计原则：金额、库存、状态这类关键字段应由后端掌握最终解释权。</p>
     */
    public SalesOrder create(@RequestBody SalesOrder order) {
        // 保存前先统一计算明细行金额和订单总额，避免前端手工传值不准确。
        if (order.getItems() != null) {
            double total = 0.0;
            for (OrderItem it : order.getItems()) {
                // lineTotal = 单价 * 数量，是订单金额结算的基础字段。
                double line = (it.getUnitPrice() == null ? 0.0 : it.getUnitPrice()) * (it.getQuantity() == null ? 0.0 : it.getQuantity());
                it.setLineTotal(line);

                // 建立双向关联，确保 JPA 在保存主表时能正确识别明细属于哪个订单。
                it.setSalesOrder(order);
                total += line;
            }
            order.setTotalAmount(total);
        }
        SalesOrder saved = salesOrderRepository.save(order);

        // 新订单创建后通过 WebSocket 广播，让前端列表无需刷新即可感知变化。
        messagingTemplate.convertAndSend("/topic/orders", saved);

        return saved;
    }

    @PostMapping("/{orderId}/create-plan")
    /*
     * 从订单快速创建生产计划。
     *
     * <p>这是一个偏演示/便捷入口的方法：直接读取订单第一条明细，生成对应生产计划。
     * 当前实现明显偏简化，适合演示或单产品订单场景；
     * 如果订单存在多条明细，企业级实现通常应为每个成品分别建计划。</p>
     */
    public ProductionPlan createPlanFromOrder(@PathVariable Long orderId, Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_PRODUCTION_MANAGER");
        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("SalesOrder not found: " + orderId));
        ProductionPlan p = new ProductionPlan();
        p.setPlanNo("PLAN-" + order.getOrderNo());
        // for simplicity, take first item product and total qty
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            OrderItem it = order.getItems().get(0);
            p.setProduct(it.getProduct());
            p.setPlannedQuantity(it.getQuantity());
        }
        p.setStartDate(LocalDateTime.now());
        p.setEndDate(LocalDateTime.now().plusDays(7));
        p.setCreatedBy(order.getCreatedBy());
        ProductionPlan saved = productionPlanRepository.save(p);

        messagingTemplate.convertAndSend("/topic/production", saved);
        return saved;
    }

    @PostMapping("/{orderId}/route-to-warehouse")
    /*
     * 销售审核通过后，将订单流转给仓库做库存核查。
     *
     * <p>这是订单工作流的关键状态推进点：
     * 销售确认后，系统需要判断现有库存能否直接履约，
     * 因此把后续决策权交给仓库模块，由其决定是直接出货还是触发生产补货。</p>
     */
    public ResponseEntity<?> routeToWarehouse(@PathVariable Long orderId,
                                              @RequestBody(required = false) WorkflowActionRequest request,
                                              Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        SalesOrder order = orderWorkflowService.routeToWarehouseCheck(orderId, authentication == null ? "" : authentication.getName());
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/warehouse-review")
    /*
     * 仓库核查订单库存情况。
     *
     * <p>真正的复杂逻辑在 OrderWorkflowService 中：
     * 会检查库存是否充足、是否需要创建生产计划、是否需要锁定库存等。
     * Controller 层只负责参数接收、权限边界与结果返回。</p>
     */
    public ResponseEntity<?> warehouseReview(@PathVariable Long orderId,
                                             @RequestBody(required = false) WorkflowActionRequest request,
                                             Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER");
        String note = request == null ? "" : request.getNote();
        OrderWorkflowService.WarehouseReviewResult result =
                orderWorkflowService.warehouseReview(orderId, authentication == null ? "" : authentication.getName(), note);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderId}/warehouse-ship")
    /*
     * 仓库确认发货。
     *
     * <p>该动作会触发成品库存真实扣减，并生成对应的库存流水，
     * 是订单从“已接单”进入“已发货”的关键节点。</p>
     */
    public ResponseEntity<?> warehouseShip(@PathVariable Long orderId,
                                           @RequestBody(required = false) WorkflowActionRequest request,
                                           Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER");
        SalesOrder order = orderWorkflowService.markOrderShipped(orderId, authentication == null ? "" : authentication.getName(), request == null ? "" : request.getNote());
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/production-complete")
    /*
     * 生产管理员回传生产完工。
     *
     * <p>完工并不等于可直接入库，系统还会生成批次并进入待质检状态，
     * 体现出“生产完成 -> 质检 -> 仓库入库”三段式流程，而不是简单一键完工。</p>
     */
    public ResponseEntity<?> productionComplete(@PathVariable Long orderId,
                                                @RequestBody(required = false) WorkflowActionRequest request,
                                                Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_PRODUCTION_MANAGER");
        SalesOrder order = orderWorkflowService.markProductionCompleted(orderId, authentication == null ? "" : authentication.getName(), request == null ? "" : request.getNote());
        return ResponseEntity.ok(order);
    }

    @GetMapping("/pending-production-stock-in")
    /*
     * 查询待生产入库的订单。
     *
     * <p>用于仓库查看哪些订单的生产已经完成且质检通过，正在等待成品入库确认。</p>
     */
    public List<SalesOrder> listPendingProductionStockIn(Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER");
        return orderWorkflowService.listOrdersPendingProductionStockIn();
    }

    @PostMapping("/{orderId}/warehouse-stock-in")
    /*
     * 仓库确认生产入库。
     *
     * <p>该动作通常发生在批次质检合格之后，会把成品数量正式计入库存，
     * 并推动订单状态向“可发货/已接单”流转。</p>
     */
    public ResponseEntity<?> warehouseStockIn(@PathVariable Long orderId,
                                              @RequestBody(required = false) WorkflowActionRequest request,
                                              Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER");
        SalesOrder order = orderWorkflowService.confirmProductionStockIn(orderId, authentication == null ? "" : authentication.getName(), request == null ? "" : request.getNote());
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/sales-decision")
    /*
     * 销售对订单做通过/拒绝决策。
     *
     * <p>这里体现了显式的业务状态分支：
     * - ACCEPT：进入仓库核查流程；
     * - REJECT：直接把订单更新为拒绝状态，并要求必须填写理由。</p>
     */
    public ResponseEntity<?> salesDecision(@PathVariable Long orderId,
                                           @RequestParam String decision,
                                           @RequestBody(required = false) WorkflowActionRequest request,
                                           Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if ("ACCEPT".equals(normalized)) {
            SalesOrder order = orderWorkflowService.routeToWarehouseCheck(orderId, authentication == null ? "" : authentication.getName());
            return ResponseEntity.ok(order);
        }
        if ("REJECT".equals(normalized)) {
            String rejectReason = request == null ? "" : request.getNote();
            if (rejectReason == null || rejectReason.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售拒绝订单时必须填写拒绝理由");
            }
            SalesOrder order = updateSalesStatus(orderId, "已拒绝", "ORDER_REJECTED", authentication);
            return ResponseEntity.ok(order);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision 仅支持 ACCEPT 或 REJECT");
    }

    @PostMapping("/{orderId}/sales-status")
    /*
     * 销售侧更新订单展示状态。
     *
     * <p>当前仅支持“已发货”“已完成”两个状态，是一种有意收口的设计：
     * 防止前端随意改状态破坏订单主流程。
     * 当状态变为“已完成”时，还会补生成销售记录，保证统计口径闭环。</p>
     */
    public ResponseEntity<?> updateBySales(@PathVariable Long orderId,
                                           @RequestParam String status,
                                           Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        String nextStatus = status == null ? "" : status.trim();
        if (!"已发货".equals(nextStatus) && !"已完成".equals(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 仅支持 已发货 或 已完成");
        }
        if ("已完成".equals(nextStatus)) {
            SalesOrder current = salesOrderRepository.findById(orderId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + orderId));
            if (!OrderWorkflowService.STATUS_SHIPPED.equals(current.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅已发货订单可标记为已完成");
            }
        }
        SalesOrder order = updateSalesStatus(orderId, nextStatus, "ORDER_STATUS_UPDATED", authentication);
        if ("已完成".equals(nextStatus)) {
            createSalesRecordIfNeeded(order, authentication == null ? "" : authentication.getName());
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/sales-records")
    /*
     * 查询销售记录。
     *
     * <p>这里区分了管理员与销售经理的可见范围：
     * - 管理员可看全部；
     * - 销售经理默认只看自己创建的销售记录。</p>
     */
    public List<SalesRecord> listSalesRecords(@RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        return querySalesRecords(startDate, endDate, authentication);
    }

    @GetMapping("/sales-records/overview")
    /*
     * 查询销售记录总览。
     *
     * <p>和 listSalesRecords 不同，这里用于图表等“全员统计”场景，
     * 因此直接返回全部符合时间范围的销售记录，而不是按当前销售经理收口。</p>
     */
    public List<SalesRecord> listSalesRecordOverview(@RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate,
                                                     Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        return queryAllSalesRecords(startDate, endDate);
    }

    @GetMapping("/sales-records/export")
    /*
     * 导出销售记录 CSV。
     *
     * <p>导出依然沿用与列表接口相同的权限边界和可见范围，
     * 防止前端通过导出接口绕过数据权限限制。</p>
     */
    public ResponseEntity<byte[]> exportSalesRecords(@RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate,
                                                     Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        List<SalesRecord> records = querySalesRecords(startDate, endDate, authentication);
        byte[] csv = CsvExportUtils.toExcelCompatibleUtf8Bytes(buildSalesRecordCsv(records));
        String fileName = "sales-records-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    private List<SalesRecord> querySalesRecords(String startDate, String endDate, Authentication authentication) {
        // 先解析时间参数，再按当前角色可见范围查询基础记录，最后统一做时间过滤。
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        List<SalesRecord> baseRecords = resolveVisibleSalesRecords(authentication);
        return filterSalesRecordsByDate(baseRecords, start, end);
    }

    private List<SalesRecord> queryAllSalesRecords(String startDate, String endDate) {
        // 总览场景不做“当前用户可见范围”裁剪，直接面向全量销售记录统计。
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        return filterSalesRecordsByDate(salesRecordRepository.findAllByOrderByCreatedAtDesc(), start, end);
    }

    private List<SalesRecord> filterSalesRecordsByDate(List<SalesRecord> baseRecords, LocalDateTime start, LocalDateTime end) {
        // 时间过滤逻辑单独抽取出来，避免列表、概览、导出三条链路重复写一遍。
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
            }
            return baseRecords.stream().filter(record -> record.getCreatedAt() != null && !record.getCreatedAt().isBefore(start) && !record.getCreatedAt().isAfter(end)).toList();
        }
        if (start != null) {
            return baseRecords.stream().filter(record -> record.getCreatedAt() != null && !record.getCreatedAt().isBefore(start)).toList();
        }
        if (end != null) {
            return baseRecords.stream().filter(record -> record.getCreatedAt() != null && !record.getCreatedAt().isAfter(end)).toList();
        }
        return baseRecords;
    }

    private List<SalesRecord> resolveVisibleSalesRecords(Authentication authentication) {
        // 管理员看全量，销售经理只看自己。
        // 这是典型的数据权限控制，和 URL 级权限控制不是一个层次：
        // URL 控制“能不能访问接口”，这里控制“能看到哪些数据”。
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return salesRecordRepository.findAllByOrderByCreatedAtDesc();
        }
        Long currentUserId = resolveCurrentUserId(authentication == null ? "" : authentication.getName());
        if (currentUserId == null) {
            return List.of();
        }
        return salesRecordRepository.findByCreatedByOrderByCreatedAtDesc(currentUserId);
    }

    private LocalDateTime parseStartDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private LocalDateTime parseEndDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atTime(23, 59, 59);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private String buildSalesRecordCsv(List<SalesRecord> records) {
        // 手工拼接 CSV，而不是引入更重的导出组件，适合当前简单导出场景。
        StringBuilder builder = new StringBuilder();
        builder.append("recordNo,orderNo,customerName,shippingAddress,totalAmount,status,createdBy,createdByName,createdAt\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (SalesRecord record : records) {
            builder.append(csvField(record.getRecordNo())).append(',')
                    .append(csvField(record.getOrderNo())).append(',')
                    .append(csvField(record.getCustomerName())).append(',')
                    .append(csvField(record.getShippingAddress())).append(',')
                    .append(record.getTotalAmount() == null ? "0" : record.getTotalAmount()).append(',')
                    .append(csvField(record.getStatus())).append(',')
                    .append(csvField(record.getCreatedBy() == null ? "" : String.valueOf(record.getCreatedBy()))).append(',')
                    .append(csvField(record.getCreatedByName())).append(',')
                    .append(csvField(record.getCreatedAt() == null ? "" : record.getCreatedAt().format(formatter)))
                    .append('\n');
        }
        return builder.toString();
    }

    private String csvField(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private SalesOrder updateSalesStatus(Long orderId, String nextStatus, String eventType, Authentication authentication) {
        // 状态更新后立即通过 WebSocket 推送，让多个角色页面实时感知订单状态变化。
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + orderId));
        order.setStatus(nextStatus);
        SalesOrder saved = salesOrderRepository.save(order);
        NotificationMessage message = new NotificationMessage(eventType, "SalesOrder", saved.getId(), saved, LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/orders", message);
        messagingTemplate.convertAndSend("/topic/orders/sales", message);
        return saved;
    }

    private void createSalesRecordIfNeeded(SalesOrder order, String operator) {
        // 销售记录具有“最终成交归档”的业务含义，因此一个订单只应对应一条销售记录。
        // 这里先查是否已存在，避免重复补录。
        if (salesRecordRepository.findBySalesOrderId(order.getId()).isPresent()) {
            return;
        }
        com.code.entity.User operatorUser = userRepository.findByEmailIgnoreCase(operator == null ? "" : operator.trim()).orElse(null);
        SalesRecord record = new SalesRecord();
        record.setRecordNo("SR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        record.setSalesOrder(order);
        record.setOrderNo(order.getOrderNo());
        record.setTotalAmount(order.getTotalAmount());
        record.setCustomerName(order.getCustomer() == null ? "" : order.getCustomer().getName());
        record.setShippingAddress(order.getShippingAddress());
        record.setStatus(order.getStatus());
        record.setCreatedBy(operatorUser == null ? null : operatorUser.getId());
        record.setCreatedByName(resolveUserDisplayName(operatorUser, operator));

        // 销售记录时间使用“生成记录的当前时间”，而不是订单创建时间，
        // 因为它代表的是业务闭环完成时点。
        record.setCreatedAt(LocalDateTime.now());
        SalesRecord savedRecord = salesRecordRepository.save(record);

        NotificationMessage message = new NotificationMessage("SALES_RECORD_CREATED", "SalesRecord", savedRecord.getId(), savedRecord, LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/orders/sales", message);
        messagingTemplate.convertAndSend("/topic/orders", message);
    }

    private void ensureRole(Authentication authentication, String... allowedRoles) {
        // 这里是 Controller 层的显式角色校验，
        // 与 Spring Security URL 级配置形成“双保险”。
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限执行该操作");
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (String role : allowedRoles) {
                if (role.equalsIgnoreCase(authority.getAuthority())) {
                    return;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限执行该操作");
    }

    private boolean hasRole(Authentication authentication, String roleName) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> roleName.equalsIgnoreCase(role));
    }

    private Long resolveCurrentUserId(String email) {
        return userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .map(com.code.entity.User::getId)
                .orElse(null);
    }

    private String resolveUserDisplayName(com.code.entity.User user, String fallback) {
        if (user == null) {
            return fallback == null || fallback.isBlank() ? null : fallback.trim();
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return fallback == null || fallback.isBlank() ? user.getEmail() : fallback.trim();
    }

    public static class WorkflowActionRequest {
        private String note;

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}

