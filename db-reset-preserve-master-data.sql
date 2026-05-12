-- 清理事务数据，保留用户/角色、客户档案、产品/原材料、BOM、仓库等主数据。
-- 保留表：users, roles, user_roles, customers, products, bom, bom_item, warehouses
-- 清空表：batch, inventory_items, mrp_requirement, order_item, procurement_weekly_plan,
--          procurement_weekly_plan_item, production_material_request,
--          production_material_request_item, production_plan, production_task,
--          production_weekly_plan, production_weekly_plan_item, purchase_order,
--          purchase_order_item, purchase_request, quality_record, sales_order,
--          sales_record, stock_transaction

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

COMMIT;

