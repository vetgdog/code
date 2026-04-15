package com.code.controller;

import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.StockTransaction;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @GetMapping
    public List<InventoryItem> listAll(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long productId,
                                       @RequestParam(required = false) Long warehouseId) {
        String normalizedKeyword = normalize(keyword);
        return inventoryItemRepository.findAll().stream()
                .filter(item -> productId == null || matchesProduct(item, productId))
                .filter(item -> warehouseId == null || matchesWarehouse(item, warehouseId))
                .filter(item -> normalizedKeyword.isEmpty() || matchesInventoryKeyword(item, normalizedKeyword))
                .sorted(Comparator.comparing(InventoryItem::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InventoryItem::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @GetMapping("/warehouses")
    public List<Warehouse> listWarehouses() {
        return warehouseRepository.findAll().stream()
                .sorted(Comparator.comparing(Warehouse::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @GetMapping("/transactions")
    public List<StockTransaction> listTransactions(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String startDate,
                                                   @RequestParam(required = false) String endDate) {
        String normalizedKeyword = normalize(keyword);
        String normalizedType = normalize(type);
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        return stockTransactionRepository.findAll().stream()
                .filter(tx -> normalizedType.isEmpty() || normalizedType.equals(normalize(tx.getTransactionType())))
                .filter(tx -> start == null || (tx.getCreatedAt() != null && !tx.getCreatedAt().isBefore(start)))
                .filter(tx -> end == null || (tx.getCreatedAt() != null && !tx.getCreatedAt().isAfter(end)))
                .filter(tx -> normalizedKeyword.isEmpty() || matchesTransactionKeyword(tx, normalizedKeyword))
                .sorted(Comparator.comparing(StockTransaction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StockTransaction::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @PostMapping("/stock-in")
    public InventoryItem stockIn(@RequestBody StockTransaction tx) {
        Product product = requireProduct(tx);
        Warehouse warehouse = requireWarehouse(tx);
        double changeQuantity = requirePositiveQuantity(tx);

        InventoryItem item = findOrCreateInventoryItem(product, warehouse);
        item.setQuantity(safeNumber(item.getQuantity()) + changeQuantity);
        item.setLot(isBlank(tx.getLot()) ? item.getLot() : tx.getLot().trim());
        item.setUpdatedAt(LocalDateTime.now());
        InventoryItem saved = inventoryItemRepository.save(item);

        tx.setTransactionNo(buildTransactionNo());
        tx.setProduct(product);
        tx.setWarehouse(warehouse);
        tx.setTransactionType("IN");
        tx.setChangeQuantity(changeQuantity);
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(tx);

        return saved;
    }

    @PostMapping("/stock-out")
    public InventoryItem stockOut(@RequestBody StockTransaction tx) {
        Product product = requireProduct(tx);
        Warehouse warehouse = requireWarehouse(tx);
        double changeQuantity = requirePositiveQuantity(tx);

        InventoryItem item = inventoryItemRepository.findAll().stream()
                .filter(i -> matchesProduct(i, product.getId()) && matchesWarehouse(i, warehouse.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到对应库存记录"));

        double availableQuantity = Math.max(0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity()));
        if (availableQuantity + 1e-6 < changeQuantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "可用库存不足，当前仅剩 " + availableQuantity);
        }

        item.setQuantity(safeNumber(item.getQuantity()) - changeQuantity);
        item.setLot(isBlank(tx.getLot()) ? item.getLot() : tx.getLot().trim());
        item.setUpdatedAt(LocalDateTime.now());
        InventoryItem saved = inventoryItemRepository.save(item);

        tx.setTransactionNo(buildTransactionNo());
        tx.setProduct(product);
        tx.setWarehouse(warehouse);
        tx.setTransactionType("OUT");
        tx.setChangeQuantity(changeQuantity);
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(tx);

        return saved;
    }

    private InventoryItem findOrCreateInventoryItem(Product product, Warehouse warehouse) {
        return inventoryItemRepository.findAll().stream()
                .filter(i -> matchesProduct(i, product.getId()) && matchesWarehouse(i, warehouse.getId()))
                .findFirst()
                .orElseGet(() -> {
                    InventoryItem item = new InventoryItem();
                    item.setProduct(product);
                    item.setWarehouse(warehouse);
                    item.setQuantity(0.0);
                    item.setReservedQuantity(0.0);
                    item.setUpdatedAt(LocalDateTime.now());
                    return item;
                });
    }

    private Product requireProduct(StockTransaction tx) {
        Long productId = tx == null || tx.getProduct() == null ? null : tx.getProduct().getId();
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择产品");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品不存在: " + productId));
    }

    private Warehouse requireWarehouse(StockTransaction tx) {
        Long warehouseId = tx == null || tx.getWarehouse() == null ? null : tx.getWarehouse().getId();
        if (warehouseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择仓库");
        }
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在: " + warehouseId));
    }

    private double requirePositiveQuantity(StockTransaction tx) {
        double quantity = safeNumber(tx == null ? null : tx.getChangeQuantity());
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "变动数量必须大于0");
        }
        return quantity;
    }

    private boolean matchesProduct(InventoryItem item, Long productId) {
        return item != null && item.getProduct() != null && productId != null && productId.equals(item.getProduct().getId());
    }

    private boolean matchesWarehouse(InventoryItem item, Long warehouseId) {
        return item != null && item.getWarehouse() != null && warehouseId != null && warehouseId.equals(item.getWarehouse().getId());
    }

    private boolean matchesInventoryKeyword(InventoryItem item, String keyword) {
        return contains(item.getProduct() == null ? null : item.getProduct().getName(), keyword)
                || contains(item.getProduct() == null ? null : item.getProduct().getSku(), keyword)
                || contains(item.getWarehouse() == null ? null : item.getWarehouse().getName(), keyword)
                || contains(item.getWarehouse() == null ? null : item.getWarehouse().getCode(), keyword)
                || contains(item.getLot(), keyword)
                || contains(item.getProduct() == null || item.getProduct().getId() == null ? null : String.valueOf(item.getProduct().getId()), keyword)
                || contains(item.getWarehouse() == null || item.getWarehouse().getId() == null ? null : String.valueOf(item.getWarehouse().getId()), keyword);
    }

    private boolean matchesTransactionKeyword(StockTransaction tx, String keyword) {
        return contains(tx.getTransactionNo(), keyword)
                || contains(tx.getProduct() == null ? null : tx.getProduct().getName(), keyword)
                || contains(tx.getProduct() == null ? null : tx.getProduct().getSku(), keyword)
                || contains(tx.getWarehouse() == null ? null : tx.getWarehouse().getName(), keyword)
                || contains(tx.getRelatedType(), keyword)
                || contains(tx.getRelatedId() == null ? null : String.valueOf(tx.getRelatedId()), keyword)
                || contains(tx.getLot(), keyword)
                || contains(tx.getRemark(), keyword);
    }

    private boolean contains(String source, String keyword) {
        return normalize(source).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private double safeNumber(Double value) {
        return value == null ? 0.0 : value;
    }

    private String buildTransactionNo() {
        return "ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private LocalDateTime parseStartDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private LocalDateTime parseEndDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atTime(23, 59, 59);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期格式错误，应为 yyyy-MM-dd");
        }
    }
}

