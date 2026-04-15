package com.code.controller;

import com.code.entity.Product;
import com.code.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public List<Product> list(@org.springframework.web.bind.annotation.RequestParam(required = false) String productType) {
        if (isBlank(productType)) {
            return productRepository.findAll();
        }
        String normalizedType = normalizeProductType(productType);
        return productRepository.findAll().stream()
                .filter(product -> normalizeProductType(product.getProductType()).equals(normalizedType))
                .collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SALES_MANAGER')")
    public ResponseEntity<?> create(@RequestBody Product product) {
        if (product == null || isBlank(product.getSku()) || isBlank(product.getName())) {
            return ResponseEntity.badRequest().body("SKU和产品名称为必填项");
        }
        product.setProductType("FINISHED_GOOD");
        if (product.getUnitPrice() == null) {
            product.setUnitPrice(0.0);
        }
        if (product.getSafetyStock() == null) {
            product.setSafetyStock(0.0);
        }
        if (product.getLeadTimeDays() == null) {
            product.setLeadTimeDays(0);
        }
        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_MANAGER')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Product request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "产品不存在: " + id));
        if (!isBlank(request.getSku())) {
            product.setSku(request.getSku().trim());
        }
        if (!isBlank(request.getName())) {
            product.setName(request.getName().trim());
        }
        product.setProductType("FINISHED_GOOD");
        product.setUnit(isBlank(request.getUnit()) ? product.getUnit() : request.getUnit().trim());
        product.setDescription(request.getDescription());
        product.setSpecification(request.getSpecification());
        product.setMaterialCategory(request.getMaterialCategory());
        product.setPreferredSupplier(request.getPreferredSupplier());
        product.setOrigin(request.getOrigin());
        if (request.getUnitPrice() != null) {
            product.setUnitPrice(request.getUnitPrice());
        }
        if (request.getSafetyStock() != null) {
            product.setSafetyStock(request.getSafetyStock());
        }
        if (request.getLeadTimeDays() != null) {
            product.setLeadTimeDays(request.getLeadTimeDays());
        }
        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_MANAGER')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "产品不存在: " + id);
        }
        productRepository.deleteById(id);
        return ResponseEntity.ok("deleted");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeProductType(String value) {
        if (isBlank(value)) {
            return "FINISHED_GOOD";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "RAW_MATERIAL".equals(normalized) ? "RAW_MATERIAL" : "FINISHED_GOOD";
    }
}

