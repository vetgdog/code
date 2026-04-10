package com.code.controller;

import com.code.entity.Product;
import com.code.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public List<Product> list() {
        return productRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SALES_MANAGER')")
    public ResponseEntity<?> create(@RequestBody Product product) {
        if (product == null || isBlank(product.getSku()) || isBlank(product.getName())) {
            return ResponseEntity.badRequest().body("SKU和产品名称为必填项");
        }
        if (product.getUnitPrice() == null) {
            product.setUnitPrice(0.0);
        }
        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

