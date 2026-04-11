package com.code.controller;

import com.code.entity.OrderItem;
import com.code.entity.SalesRecord;
import com.code.entity.SalesOrder;
import com.code.repository.SalesOrderRepository;
import com.code.entity.ProductionPlan;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesRecordRepository;
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

@RestController
@RequestMapping("/api/v1/orders")
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
    private OrderWorkflowService orderWorkflowService;

    @GetMapping
    public List<SalesOrder> list() {
        return salesOrderRepository.findAll();
    }

    @PostMapping
    public SalesOrder create(@RequestBody SalesOrder order) {
        // calculate totals for simplicity
        if (order.getItems() != null) {
            double total = 0.0;
            for (OrderItem it : order.getItems()) {
                double line = (it.getUnitPrice() == null ? 0.0 : it.getUnitPrice()) * (it.getQuantity() == null ? 0.0 : it.getQuantity());
                it.setLineTotal(line);
                it.setSalesOrder(order);
                total += line;
            }
            order.setTotalAmount(total);
        }
        SalesOrder saved = salesOrderRepository.save(order);

        // notify via websocket
        messagingTemplate.convertAndSend("/topic/orders", saved);

        return saved;
    }

    @PostMapping("/{orderId}/create-plan")
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
    public ResponseEntity<?> routeToWarehouse(@PathVariable Long orderId,
                                              @RequestBody(required = false) WorkflowActionRequest request,
                                              Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        SalesOrder order = orderWorkflowService.routeToWarehouseCheck(orderId, authentication == null ? "" : authentication.getName());
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/warehouse-review")
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
    public ResponseEntity<?> warehouseShip(@PathVariable Long orderId,
                                           @RequestBody(required = false) WorkflowActionRequest request,
                                           Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER");
        SalesOrder order = orderWorkflowService.markOrderShipped(orderId, authentication == null ? "" : authentication.getName(), request == null ? "" : request.getNote());
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/production-complete")
    public ResponseEntity<?> productionComplete(@PathVariable Long orderId,
                                                @RequestBody(required = false) WorkflowActionRequest request,
                                                Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_PRODUCTION_MANAGER");
        SalesOrder order = orderWorkflowService.markProductionCompleted(orderId, authentication == null ? "" : authentication.getName(), request == null ? "" : request.getNote());
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/sales-decision")
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
            SalesOrder order = updateSalesStatus(orderId, "已拒绝", "ORDER_REJECTED", authentication);
            return ResponseEntity.ok(order);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision 仅支持 ACCEPT 或 REJECT");
    }

    @PostMapping("/{orderId}/sales-status")
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
    public List<SalesRecord> listSalesRecords(@RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        return querySalesRecords(startDate, endDate);
    }

    @GetMapping("/sales-records/export")
    public ResponseEntity<String> exportSalesRecords(@RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate,
                                                     Authentication authentication) {
        ensureRole(authentication, "ROLE_ADMIN", "ROLE_SALES_MANAGER");
        List<SalesRecord> records = querySalesRecords(startDate, endDate);
        String csv = buildSalesRecordCsv(records);
        String fileName = "sales-records-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    private List<SalesRecord> querySalesRecords(String startDate, String endDate) {
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
            }
            return salesRecordRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        }
        if (start != null) {
            return salesRecordRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(start);
        }
        if (end != null) {
            return salesRecordRepository.findByCreatedAtLessThanEqualOrderByCreatedAtDesc(end);
        }
        return salesRecordRepository.findAllByOrderByCreatedAtDesc();
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
        StringBuilder builder = new StringBuilder();
        builder.append("recordNo,orderNo,customerName,shippingAddress,totalAmount,status,createdAt\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (SalesRecord record : records) {
            builder.append(csvField(record.getRecordNo())).append(',')
                    .append(csvField(record.getOrderNo())).append(',')
                    .append(csvField(record.getCustomerName())).append(',')
                    .append(csvField(record.getShippingAddress())).append(',')
                    .append(record.getTotalAmount() == null ? "0" : record.getTotalAmount()).append(',')
                    .append(csvField(record.getStatus())).append(',')
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
        if (salesRecordRepository.findBySalesOrderId(order.getId()).isPresent()) {
            return;
        }
        SalesRecord record = new SalesRecord();
        record.setRecordNo("SR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        record.setSalesOrder(order);
        record.setOrderNo(order.getOrderNo());
        record.setTotalAmount(order.getTotalAmount());
        record.setCustomerName(order.getCustomer() == null ? "" : order.getCustomer().getName());
        record.setShippingAddress(order.getShippingAddress());
        record.setStatus(order.getStatus());
        record.setCreatedBy(order.getCreatedBy());
        record.setCreatedAt(LocalDateTime.now());
        SalesRecord savedRecord = salesRecordRepository.save(record);

        NotificationMessage message = new NotificationMessage("SALES_RECORD_CREATED", "SalesRecord", savedRecord.getId(), savedRecord, LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/orders/sales", message);
        messagingTemplate.convertAndSend("/topic/orders", message);
    }

    private void ensureRole(Authentication authentication, String... allowedRoles) {
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

