package com.code.controller;

import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.StockTransaction;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.StockTransactionRepository;
import com.code.repository.UserRepository;
import com.code.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 库存台账控制器。
 *
 * <p>负责仓库日常最基础的三类动作：查看库存余额、查看库存流水、执行手工出入库。它本身不是完整的仓储流程引擎，
 * 但承担着“数量变更必须留下流水证据”的底线职责，因此每次库存调整都会同步写入 {@link StockTransaction}。</p>
 *
 * <p>当前实现偏向中小规模项目的可读性方案：查询主要通过内存流式过滤完成，方便理解业务规则；如果后续库存记录
 * 和流水显著增大，最优先的演进方向会是数据库条件查询、分页、以及产品仓库维度的唯一约束。</p>
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    /**
     * 库存余额仓库，查询和更新“当前还剩多少”。
     */
    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    /**
     * 库存流水仓库，记录每一次入库/出库的审计痕迹。
     */
    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    /**
     * 产品主数据仓库，确保库存动作绑定的是系统中真实存在的产品。
     */
    @Autowired
    private ProductRepository productRepository;

    /**
     * 仓库主数据仓库，限制库存动作只能发生在合法仓库上。
     */
    @Autowired
    private WarehouseRepository warehouseRepository;

    /**
     * 当前操作人解析仓库，用于给库存流水补齐操作人审计字段。
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * 查询库存台账。
     *
     * <p>支持产品、仓库、关键字叠加过滤。排序优先返回最近更新的数据，能让仓库人员更快看到最近发生过变化的库存。</p>
     */
    @GetMapping
    public List<InventoryItem> listAll(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long productId,
                                       @RequestParam(required = false) Long warehouseId) {
        String normalizedKeyword = normalize(keyword);

        // 当前实现直接拉取全部库存再在内存中过滤，优点是过滤规则集中、修改成本低；
        // 缺点是数据量增大后会放大内存和序列化开销，后续可下沉到 Repository + 分页查询。
        return inventoryItemRepository.findAll().stream()
                .filter(item -> productId == null || matchesProduct(item, productId))
                .filter(item -> warehouseId == null || matchesWarehouse(item, warehouseId))
                .filter(item -> normalizedKeyword.isEmpty() || matchesInventoryKeyword(item, normalizedKeyword))
                .sorted(Comparator.comparing(InventoryItem::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InventoryItem::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 查询仓库主数据，主要供前端下拉筛选与出入库表单使用。
     */
    @GetMapping("/warehouses")
    public List<Warehouse> listWarehouses() {
        return warehouseRepository.findAll().stream()
                .sorted(Comparator.comparing(Warehouse::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 查询库存流水。
     *
     * <p>这是库存审计与追责的关键入口，可按交易类型、关键字、日期范围过滤。开始/结束日期只要求 yyyy-MM-dd，
     * 控制器再统一扩展成当天起止时间，降低前端传参复杂度。</p>
     */
    @GetMapping("/transactions")
    public List<StockTransaction> listTransactions(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String startDate,
                                                   @RequestParam(required = false) String endDate) {
        String normalizedKeyword = normalize(keyword);
        String normalizedType = normalize(type);

        // 控制器负责把“日期字符串”翻译成真正可比较的时间边界，
        // 这样前端无需自行拼 00:00:00 / 23:59:59，接口契约更简单。
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        // 日期过滤使用“包含式区间”，符合后台报表/台账查询的直觉：
        // 选择 2026-05-10 作为结束日时，用户通常希望看到当天全部流水而不是只看到 00:00:00 之前的数据。
        return stockTransactionRepository.findAll().stream()
                .filter(tx -> normalizedType.isEmpty() || normalizedType.equals(normalize(tx.getTransactionType())))
                .filter(tx -> start == null || (tx.getCreatedAt() != null && !tx.getCreatedAt().isBefore(start)))
                .filter(tx -> end == null || (tx.getCreatedAt() != null && !tx.getCreatedAt().isAfter(end)))
                .filter(tx -> normalizedKeyword.isEmpty() || matchesTransactionKeyword(tx, normalizedKeyword))
                .sorted(Comparator.comparing(StockTransaction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StockTransaction::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 手工入库。
     *
     * <p>流程为：校验入参 → 定位或创建库存记录 → 增加库存余额 → 记录 IN 流水。控制器没有接收独立 DTO，
     * 而是直接复用库存流水实体作为请求体，因此前端一次提交既能表达库存动作，也能附带批次、备注等审计字段。</p>
     */
    @PostMapping("/stock-in")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public InventoryItem stockIn(@RequestBody StockTransaction tx, Authentication authentication) {
        Product product = requireProduct(tx);
        Warehouse warehouse = requireWarehouse(tx);
        double changeQuantity = requirePositiveQuantity(tx);
        User operator = resolveCurrentUser(authentication);

        // 若产品-仓库维度的库存记录尚不存在，则在这里按 0 初始化，
        // 说明当前系统以“余额首次发生时自动建账”的方式维护库存台账。
        InventoryItem item = findOrCreateInventoryItem(product, warehouse);
        item.setQuantity(safeNumber(item.getQuantity()) + changeQuantity);
        item.setLot(isBlank(tx.getLot()) ? item.getLot() : tx.getLot().trim());
        item.setUpdatedAt(LocalDateTime.now());
        InventoryItem saved = inventoryItemRepository.save(item);

        // 入库动作修改余额后，必须同步落一条 IN 流水；
        // 否则库存虽然变了，但后续无法回答“为什么变、谁改的、关联什么业务”。
        tx.setTransactionNo(buildTransactionNo());
        tx.setProduct(product);
        tx.setWarehouse(warehouse);
        tx.setTransactionType("IN");
        tx.setChangeQuantity(changeQuantity);
        tx.setCreatedBy(operator == null ? null : operator.getId());
        tx.setCreatedByName(resolveDisplayName(operator, authentication == null ? "" : authentication.getName()));
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(tx);

        return saved;
    }

    /**
     * 手工出库。
     *
     * <p>出库时校验的是“可用库存”而不是总库存，即 `quantity - reservedQuantity`。这层保护可以防止已经被订单、
     * 生产等流程锁定的库存再次被人工扣减，是库存一致性最核心的规则之一。</p>
     */
    @PostMapping("/stock-out")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public InventoryItem stockOut(@RequestBody StockTransaction tx, Authentication authentication) {
        Product product = requireProduct(tx);
        Warehouse warehouse = requireWarehouse(tx);
        double changeQuantity = requirePositiveQuantity(tx);
        User operator = resolveCurrentUser(authentication);

        // 出库需要命中已有库存记录；与入库不同，这里不会自动创建空账，
        // 因为“没有库存记录却允许出库”通常意味着主数据或入库流程有问题。
        InventoryItem item = inventoryItemRepository.findAll().stream()
                .filter(i -> matchesProduct(i, product.getId()) && matchesWarehouse(i, warehouse.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到对应库存记录"));

        double availableQuantity = Math.max(0, safeNumber(item.getQuantity()) - safeNumber(item.getReservedQuantity()));
        if (availableQuantity + 1e-6 < changeQuantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "可用库存不足，当前仅剩 " + availableQuantity);
        }

        // 这里扣的是总余额 quantity；预留量 reservedQuantity 只是校验约束，不会在手工出库时顺带减少。
        // 这体现了“预留是另一条业务线的锁库语义”，不能被手工台账操作随意篡改。
        item.setQuantity(safeNumber(item.getQuantity()) - changeQuantity);
        item.setLot(isBlank(tx.getLot()) ? item.getLot() : tx.getLot().trim());
        item.setUpdatedAt(LocalDateTime.now());
        InventoryItem saved = inventoryItemRepository.save(item);

        tx.setTransactionNo(buildTransactionNo());
        tx.setProduct(product);
        tx.setWarehouse(warehouse);
        tx.setTransactionType("OUT");
        tx.setChangeQuantity(changeQuantity);
        tx.setCreatedBy(operator == null ? null : operator.getId());
        tx.setCreatedByName(resolveDisplayName(operator, authentication == null ? "" : authentication.getName()));
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(tx);

        return saved;
    }

    /**
     * 查找或创建产品-仓库维度的库存对象。
     *
     * <p>这反映出当前系统库存粒度以“仓库”为边界，而不是更细的库位/托盘/序列号粒度。若未来要引入更精细的 WMS，
     * 该方法会是重要的重构切入点。</p>
     */
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

    /**
     * 校验并加载产品实体。
     *
     * <p>请求体里只信任产品 id，不信任前端回传的其他产品字段；真正的产品信息必须重新查库确认，
     * 这样可以避免前端伪造 SKU、名称甚至产品类型导致库存挂错对象。</p>
     */
    private Product requireProduct(StockTransaction tx) {
        Long productId = tx == null || tx.getProduct() == null ? null : tx.getProduct().getId();
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择产品");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品不存在: " + productId));
    }

    /**
     * 校验并加载仓库实体，避免库存动作落到不存在或被前端伪造的仓库上。
     */
    private Warehouse requireWarehouse(StockTransaction tx) {
        Long warehouseId = tx == null || tx.getWarehouse() == null ? null : tx.getWarehouse().getId();
        if (warehouseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择仓库");
        }
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在: " + warehouseId));
    }

    /**
     * 强制数量为正数。
     *
     * <p>这样能防止把“负入库=出库”或“负出库=入库”这种语义混淆塞进同一接口，保持接口职责单一明确。</p>
     */
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

    /**
     * 库存关键字搜索覆盖产品名、SKU、仓库名、仓库编码、批次与关联 id，属于后台管理场景常见的宽松检索设计。
     */
    private boolean matchesInventoryKeyword(InventoryItem item, String keyword) {
        return contains(item.getProduct() == null ? null : item.getProduct().getName(), keyword)
                || contains(item.getProduct() == null ? null : item.getProduct().getSku(), keyword)
                || contains(item.getWarehouse() == null ? null : item.getWarehouse().getName(), keyword)
                || contains(item.getWarehouse() == null ? null : item.getWarehouse().getCode(), keyword)
                || contains(item.getLot(), keyword)
                || contains(item.getProduct() == null || item.getProduct().getId() == null ? null : String.valueOf(item.getProduct().getId()), keyword)
                || contains(item.getWarehouse() == null || item.getWarehouse().getId() == null ? null : String.valueOf(item.getWarehouse().getId()), keyword);
    }

    /**
     * 库存流水关键字搜索会同时匹配单号、产品、仓库、关联业务单据和备注，方便快速定位异常操作来源。
     */
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

    /**
     * 解析当前登录用户，供库存流水补齐 createdBy / createdByName。
     *
     * <p>这里宽松返回 null 而不是抛错，是因为真正的权限控制已由 `@PreAuthorize` 负责；
     * 该方法更偏向“审计字段增强”，不是认证主入口。</p>
     */
    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName().trim()).orElse(null);
    }

    /**
     * 生成库存流水展示姓名：优先用用户真实姓名，缺失时再退回登录名，提升审计可读性。
     */
    private String resolveDisplayName(User user, String fallback) {
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return isBlank(fallback) ? null : fallback.trim();
    }

    /**
     * 开始日期按当天 00:00:00 解析，便于做包含式起始过滤。
     */
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

    /**
     * 结束日期按当天 23:59:59 解析，保证用户选择某一天时，该天全部流水都会被纳入结果。
     */
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

