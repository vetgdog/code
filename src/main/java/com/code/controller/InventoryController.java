package com.code.controller;

import com.code.entity.InventoryItem;
import com.code.entity.StockTransaction;
import com.code.repository.InventoryItemRepository;
import com.code.repository.StockTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @GetMapping
    public List<InventoryItem> listAll() {
        return inventoryItemRepository.findAll();
    }

    @PostMapping("/stock-in")
    public InventoryItem stockIn(@RequestBody StockTransaction tx) {
        // create or update inventory item
        InventoryItem item = inventoryItemRepository.findAll().stream()
                .filter(i -> i.getProduct().getId().equals(tx.getProduct().getId()) && i.getWarehouse().getId().equals(tx.getWarehouse().getId()))
                .findFirst().orElseGet(() -> {
                    InventoryItem ni = new InventoryItem();
                    ni.setProduct(tx.getProduct());
                    ni.setWarehouse(tx.getWarehouse());
                    return ni;
                });

        item.setQuantity((item.getQuantity() == null ? 0.0 : item.getQuantity()) + (tx.getChangeQuantity() == null ? 0.0 : tx.getChangeQuantity()));
        InventoryItem saved = inventoryItemRepository.save(item);

        tx.setTransactionType("IN");
        stockTransactionRepository.save(tx);

        return saved;
    }

    @PostMapping("/stock-out")
    public InventoryItem stockOut(@RequestBody StockTransaction tx) {
        InventoryItem item = inventoryItemRepository.findAll().stream()
                .filter(i -> i.getProduct().getId().equals(tx.getProduct().getId()) && i.getWarehouse().getId().equals(tx.getWarehouse().getId()))
                .findFirst().orElseThrow(() -> new RuntimeException("Inventory item not found"));

        item.setQuantity((item.getQuantity() == null ? 0.0 : item.getQuantity()) - (tx.getChangeQuantity() == null ? 0.0 : tx.getChangeQuantity()));
        InventoryItem saved = inventoryItemRepository.save(item);

        tx.setTransactionType("OUT");
        stockTransactionRepository.save(tx);

        return saved;
    }
}

