package com.code.controller;

import com.code.entity.SalesOrder;
import com.code.repository.SalesOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    // portal: list orders for a specific customer id
    @GetMapping("/{customerId}/orders")
    public List<SalesOrder> listOrders(@PathVariable Long customerId) {
        // for simplicity, load all and filter (in production use repo query)
        return salesOrderRepository.findAll().stream().filter(o -> o.getCustomer() != null && o.getCustomer().getId().equals(customerId)).toList();
    }

    @GetMapping("/orders/{orderId}")
    public SalesOrder getOrder(@PathVariable Long orderId) {
        return salesOrderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
    }
}

