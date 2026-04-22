package com.code.config;

import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.Role;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.RoleRepository;
import com.code.repository.UserRepository;
import com.code.repository.WarehouseRepository;
import com.code.support.WarehouseDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.init.demo-master-data.enabled", havingValue = "true")
public class DemoMasterDataInitializer implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "密码123456";

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role supplierRole = upsertRole("ROLE_SUPPLIER", "供应商账号");
        Role salesManagerRole = upsertRole("ROLE_SALES_MANAGER", "销售管理员账号");

        Warehouse rawMaterialWarehouse = upsertWarehouse(
                WarehouseDefaults.RAW_MATERIAL_WAREHOUSE_CODE,
                WarehouseDefaults.RAW_MATERIAL_WAREHOUSE_NAME,
                WarehouseDefaults.RAW_MATERIAL_WAREHOUSE_LOCATION
        );
        Warehouse finishedGoodsWarehouse = upsertWarehouse(
                WarehouseDefaults.FINISHED_GOODS_WAREHOUSE_CODE,
                WarehouseDefaults.FINISHED_GOODS_WAREHOUSE_NAME,
                WarehouseDefaults.FINISHED_GOODS_WAREHOUSE_LOCATION
        );

        User supplierOne = upsertUser("gongyingshang", "gongyingshang1@qq.com", "gongyingshang", "123456", supplierRole);
        User supplierTwo = upsertUser("gongyingshang2", "gongyingshang2@qq.com", "gongyingshang2", "123456", supplierRole);
        upsertUser("xiaoshou1", "xiaoshou1@qq.com", "xiaoshou1", "123456", salesManagerRole);
        upsertUser("xiaoshou2", "xiaoshou2@qq.com", "xiaoshou2", "123456", salesManagerRole);

        List<Product> rawMaterials = List.of(
                buildRawMaterial("RM-GYS1-001", "冷轧钢板", "钢材", "Q235 / 1.5mm", "kg", 5.60, supplierOne.getCode(), "上海", 200.0, 7, "用于柜体外壳钣金加工"),
                buildRawMaterial("RM-GYS1-002", "镀锌钢卷", "钢材", "DX51D / 1.2mm", "kg", 6.10, supplierOne.getCode(), "无锡", 180.0, 6, "用于防腐结构件生产"),
                buildRawMaterial("RM-GYS2-001", "铝合金板", "有色金属", "5052 / 3mm", "kg", 16.80, supplierTwo.getCode(), "佛山", 90.0, 5, "用于轻量化面板与支架"),
                buildRawMaterial("RM-GYS2-002", "环氧粉末涂料", "化工辅料", "RAL7035 / 25kg", "箱", 320.0, supplierTwo.getCode(), "常州", 35.0, 4, "用于成品表面喷涂" )
        );

        List<Product> finishedGoods = List.of(
                buildFinishedGood("FG-XS-001", "精密配电柜", "PX-01 / 标准型", "台", 6800.0, 10.0, "面向工厂配电场景的标准柜体产品"),
                buildFinishedGood("FG-XS-002", "工业控制柜", "CTRL-02 / 加强型", "台", 9200.0, 8.0, "适用于设备联控与自动化产线"),
                buildFinishedGood("FG-XS-003", "钣金机箱", "CAB-03 / 壁挂型", "台", 2600.0, 15.0, "公司标准化钣金机箱产品" )
        );

        rawMaterials.stream()
                .map(this::upsertProduct)
                .forEach(product -> ensureInventoryStub(product, rawMaterialWarehouse));

        finishedGoods.stream()
                .map(this::upsertProduct)
                .forEach(product -> ensureInventoryStub(product, finishedGoodsWarehouse));
    }

    private Role upsertRole(String roleName, String description) {
        Role role = roleRepository.findByName(roleName).orElseGet(Role::new);
        role.setName(roleName);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    private Warehouse upsertWarehouse(String code, String name, String location) {
        Warehouse warehouse = warehouseRepository.findByCodeIgnoreCase(code).orElseGet(Warehouse::new);
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setLocation(location);
        if (warehouse.getCreatedAt() == null) {
            warehouse.setCreatedAt(LocalDateTime.now());
        }
        return warehouseRepository.save(warehouse);
    }

    private User upsertUser(String username, String email, String fullName, String phone, Role role) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .or(() -> userRepository.findByUsername(username))
                .orElseGet(User::new);
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        if (user.getPassword() == null || user.getPassword().isBlank() || !passwordEncoder.matches(DEFAULT_PASSWORD, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private Product upsertProduct(Product seed) {
        Product product = productRepository.findBySkuIgnoreCase(seed.getSku()).orElseGet(Product::new);
        product.setSku(seed.getSku());
        product.setName(seed.getName());
        product.setProductType(seed.getProductType());
        product.setMaterialCategory(seed.getMaterialCategory());
        product.setSpecification(seed.getSpecification());
        product.setPreferredSupplier(seed.getPreferredSupplier());
        product.setOrigin(seed.getOrigin());
        product.setDescription(seed.getDescription());
        product.setUnit(seed.getUnit());
        product.setWeight(seed.getWeight());
        product.setUnitPrice(seed.getUnitPrice());
        product.setSafetyStock(seed.getSafetyStock());
        product.setLeadTimeDays(seed.getLeadTimeDays());
        if (product.getCreatedAt() == null) {
            product.setCreatedAt(LocalDateTime.now());
        }
        return productRepository.save(product);
    }

    private void ensureInventoryStub(Product product, Warehouse warehouse) {
        if (product == null || product.getId() == null || warehouse == null || warehouse.getId() == null) {
            return;
        }
        Optional<InventoryItem> existing = inventoryItemRepository.findByProductId(product.getId()).stream()
                .filter(item -> item.getWarehouse() != null && warehouse.getId().equals(item.getWarehouse().getId()))
                .findFirst();
        if (existing.isPresent()) {
            return;
        }
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setProduct(product);
        inventoryItem.setWarehouse(warehouse);
        inventoryItem.setQuantity(0.0);
        inventoryItem.setReservedQuantity(0.0);
        inventoryItem.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(inventoryItem);
    }

    private Product buildRawMaterial(String sku,
                                     String name,
                                     String materialCategory,
                                     String specification,
                                     String unit,
                                     double unitPrice,
                                     String preferredSupplier,
                                     String origin,
                                     double safetyStock,
                                     int leadTimeDays,
                                     String description) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setProductType("RAW_MATERIAL");
        product.setMaterialCategory(materialCategory);
        product.setSpecification(specification);
        product.setUnit(unit);
        product.setUnitPrice(unitPrice);
        product.setPreferredSupplier(preferredSupplier);
        product.setOrigin(origin);
        product.setSafetyStock(safetyStock);
        product.setLeadTimeDays(leadTimeDays);
        product.setDescription(description);
        return product;
    }

    private Product buildFinishedGood(String sku,
                                      String name,
                                      String specification,
                                      String unit,
                                      double unitPrice,
                                      double safetyStock,
                                      String description) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setProductType("FINISHED_GOOD");
        product.setSpecification(specification);
        product.setUnit(unit);
        product.setUnitPrice(unitPrice);
        product.setSafetyStock(safetyStock);
        product.setDescription(description);
        return product;
    }
}
