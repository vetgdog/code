package com.code.support;

import com.code.entity.Product;

public final class WarehouseDefaults {

    public static final String RAW_MATERIAL_WAREHOUSE_CODE = "WH-RAW-001";
    public static final String RAW_MATERIAL_WAREHOUSE_NAME = "原材料仓库";
    public static final String RAW_MATERIAL_WAREHOUSE_LOCATION = "原材料库区";

    public static final String FINISHED_GOODS_WAREHOUSE_CODE = "WH-FG-001";
    public static final String FINISHED_GOODS_WAREHOUSE_NAME = "成品仓库";
    public static final String FINISHED_GOODS_WAREHOUSE_LOCATION = "成品库区";

    private WarehouseDefaults() {
    }

    public static String defaultWarehouseCodeFor(Product product) {
        return isRawMaterial(product) ? RAW_MATERIAL_WAREHOUSE_CODE : FINISHED_GOODS_WAREHOUSE_CODE;
    }

    public static boolean isRawMaterial(Product product) {
        return product != null && "RAW_MATERIAL".equalsIgnoreCase(product.getProductType());
    }
}
