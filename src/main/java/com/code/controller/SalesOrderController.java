package com.code.controller;

import com.code.entity.OrderItem;
import com.code.entity.SalesOrder;
import com.code.repository.SalesOrderRepository;
import com.code.entity.ProductionPlan;
import com.code.repository.ProductionPlanRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class SalesOrderController {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

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
    public ProductionPlan createPlanFromOrder(@PathVariable Long orderId) {
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
}

