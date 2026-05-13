package com.code.config;

import com.code.entity.Product;
import com.code.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.init.raw-materials.enabled", havingValue = "true")
/*
 * 原材料基础数据初始化器。
 *
 * <p>相比 `DemoMasterDataInitializer` 的“全套演示主数据”，该组件更聚焦于补齐一批可直接用于采购、库存、周计划演示的原材料档案。
 * 它通过配置开关控制是否启用，避免在正式库启动时无意插入演示物料。</p>
 */
public class RawMaterialDataInitializer implements ApplicationRunner {

    @Autowired
    private ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        // 默认物料清单写在代码里，适合样例环境快速起盘；
        // 如果后续需要更强运维灵活性，可以迁移到 SQL 或外部配置文件。
        List<Product> defaults = List.of(
                buildMaterial("RM-STEEL-001", "冷轧钢板", "钢材", "Q235 / 2mm", "kg", 5.86, "华东钢材供应商", "上海", 200.0, 7, "用于钣金件生产"),
                buildMaterial("RM-STEEL-002", "304 不锈钢卷", "钢材", "304 / 1.2mm", "kg", 8.75, "华东钢材供应商", "无锡", 150.0, 6, "用于机柜外壳与结构件加工"),
                buildMaterial("RM-AL-001", "铝合金板材", "有色金属", "5052 / 3mm", "kg", 16.40, "华南金属材料公司", "佛山", 80.0, 5, "用于轻量化面板与支架"),
                buildMaterial("RM-PAINT-001", "环氧底漆", "化工辅料", "灰色 / 20kg", "桶", 298.00, "江苏快涂化工", "常州", 30.0, 4, "用于金属表面防腐打底"),
                buildMaterial("RM-PACK-001", "防锈包装膜", "包装辅料", "PE / 500mm", "卷", 42.50, "苏州工业包装", "苏州", 60.0, 3, "用于成品与半成品周转包装")
        );

        for (Product material : defaults) {
            // 只在 SKU 不存在时插入，确保多次启动幂等，且不会覆盖人工维护后的正式主数据。
            if (productRepository.findBySkuIgnoreCase(material.getSku()).isEmpty()) {
                productRepository.save(material);
            }
        }
    }

    private Product buildMaterial(String sku,
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
        // 原材料模板在统一产品表中通过 productType 区分，
        // 这样采购、库存、预警逻辑都能复用同一 Product 主数据结构。
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
}

