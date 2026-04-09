package com.code.controller;

import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseRequest;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.PurchaseRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/procurement")
public class ProcurementController {

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @GetMapping("/requests")
    public List<PurchaseRequest> listRequests() {
        return purchaseRequestRepository.findAll();
    }

    @PostMapping("/orders")
    public PurchaseOrder createOrder(@RequestBody PurchaseOrder po) {
        // calculate totals
        if (po.getItems() != null) {
            double total = 0.0;
            for (var it : po.getItems()) {
                double line = (it.getUnitPrice() == null ? 0.0 : it.getUnitPrice()) * (it.getQuantity() == null ? 0.0 : it.getQuantity());
                it.setLineTotal(line);
                it.setPurchaseOrder(po);
                total += line;
            }
            po.setTotalAmount(total);
        }
        return purchaseOrderRepository.save(po);
    }
}

