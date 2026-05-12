-- 清理数据库中的业务数据与非必要主数据，仅保留：
-- 1) 用户数据：users, roles, user_roles
-- 2) 产品/原材料/供应商提供的原材料信息：products
-- 说明：
-- - 原材料与成品均存放在 products 表中，通过 product_type / preferred_supplier 等字段区分。
-- - 其余信息（客户、仓库、BOM、库存、订单、采购、生产、质检、周计划、MRP 等）全部清空。

START TRANSACTION;

DELETE FROM quality_record;
DELETE FROM batch;
DELETE FROM production_material_request_item;
DELETE FROM production_material_request;
DELETE FROM production_task;
DELETE FROM production_plan;
DELETE FROM procurement_weekly_plan_item;
DELETE FROM procurement_weekly_plan;
DELETE FROM production_weekly_plan_item;
DELETE FROM production_weekly_plan;
DELETE FROM purchase_order_item;
DELETE FROM purchase_order;
DELETE FROM purchase_request;
DELETE FROM sales_record;
DELETE FROM order_item;
DELETE FROM sales_order;
DELETE FROM stock_transaction;
DELETE FROM inventory_items;
DELETE FROM mrp_requirement;
DELETE FROM bom_item;
DELETE FROM bom;
DELETE FROM customers;
DELETE FROM warehouses;

ALTER TABLE quality_record AUTO_INCREMENT = 1;
ALTER TABLE batch AUTO_INCREMENT = 1;
ALTER TABLE production_material_request_item AUTO_INCREMENT = 1;
ALTER TABLE production_material_request AUTO_INCREMENT = 1;
ALTER TABLE production_task AUTO_INCREMENT = 1;
ALTER TABLE production_plan AUTO_INCREMENT = 1;
ALTER TABLE procurement_weekly_plan_item AUTO_INCREMENT = 1;
ALTER TABLE procurement_weekly_plan AUTO_INCREMENT = 1;
ALTER TABLE production_weekly_plan_item AUTO_INCREMENT = 1;
ALTER TABLE production_weekly_plan AUTO_INCREMENT = 1;
ALTER TABLE purchase_order_item AUTO_INCREMENT = 1;
ALTER TABLE purchase_order AUTO_INCREMENT = 1;
ALTER TABLE purchase_request AUTO_INCREMENT = 1;
ALTER TABLE sales_record AUTO_INCREMENT = 1;
ALTER TABLE order_item AUTO_INCREMENT = 1;
ALTER TABLE sales_order AUTO_INCREMENT = 1;
ALTER TABLE stock_transaction AUTO_INCREMENT = 1;
ALTER TABLE inventory_items AUTO_INCREMENT = 1;
ALTER TABLE mrp_requirement AUTO_INCREMENT = 1;
ALTER TABLE bom_item AUTO_INCREMENT = 1;
ALTER TABLE bom AUTO_INCREMENT = 1;
ALTER TABLE customers AUTO_INCREMENT = 1;
ALTER TABLE warehouses AUTO_INCREMENT = 1;

COMMIT;

