START TRANSACTION;
SET NAMES utf8mb4;

SET @status_done := CONVERT(0xE5B7B2E5AE8CE68890 USING utf8mb4);
SET @status_warehoused_cn := CONVERT(0xE5B7B2E585A5E68890E59381E5BA93 USING utf8mb4);
SET @status_pass := CONVERT(0xE59088E6A0BC USING utf8mb4);

SET @sales_user_id := (SELECT id FROM users WHERE email = 'xs@qq.com' LIMIT 1);
SET @sales_user_name := (SELECT COALESCE(full_name, username) FROM users WHERE id = @sales_user_id LIMIT 1);
SET @warehouse_user_id := (SELECT id FROM users WHERE email = 'ck@qq.com' LIMIT 1);
SET @warehouse_user_name := (SELECT COALESCE(full_name, username) FROM users WHERE id = @warehouse_user_id LIMIT 1);
SET @prod_user_1 := (SELECT id FROM users WHERE email = 'sc@qq.com' LIMIT 1);
SET @prod_user_1_name := (SELECT COALESCE(full_name, username) FROM users WHERE id = @prod_user_1 LIMIT 1);
SET @prod_user_2 := (SELECT id FROM users WHERE email = 'sc2@qq.com' LIMIT 1);
SET @prod_user_2_name := (SELECT COALESCE(full_name, username) FROM users WHERE id = @prod_user_2 LIMIT 1);
SET @quality_user_id := (SELECT id FROM users WHERE email = 'zj@qq.com' LIMIT 1);
SET @quality_user_name := (SELECT COALESCE(full_name, username) FROM users WHERE id = @quality_user_id LIMIT 1);

SET @wh_fg := (SELECT id FROM warehouses WHERE code = 'WH-FG-001' LIMIT 1);
SET @wh_rm := (SELECT id FROM warehouses WHERE code = 'WH-RAW-001' LIMIT 1);

SET @customer_id := (SELECT id FROM customers ORDER BY id LIMIT 1);
INSERT INTO customers (code, name, contact, phone, email, address, created_at)
SELECT 'CUS-LW2605-001', 'gk', 'gk', '123456', 'gk@qq.com', 'Hangzhou Client Address', '2026-05-04 08:00:00'
WHERE @customer_id IS NULL;
SET @customer_id := (SELECT id FROM customers ORDER BY id LIMIT 1);
SET @customer_name := (SELECT name FROM customers WHERE id = @customer_id LIMIT 1);

SET @fg1 := (SELECT id FROM products WHERE sku = 'FG-XS-001' LIMIT 1);
SET @fg2 := (SELECT id FROM products WHERE sku = 'FG-XS-002' LIMIT 1);
SET @fg3 := (SELECT id FROM products WHERE sku = 'FG-XS-003' LIMIT 1);
SET @fg4 := (SELECT id FROM products WHERE sku = 'FG-XS-004' LIMIT 1);
SET @fg11 := (SELECT id FROM products WHERE sku = 'FG-TEST-011' LIMIT 1);
SET @fg12 := (SELECT id FROM products WHERE sku = 'FG-TEST-012' LIMIT 1);

SET @rm5 := (SELECT id FROM products WHERE sku = 'RM-GYS1-001' LIMIT 1);
SET @rm6 := (SELECT id FROM products WHERE sku = 'RM-GYS1-002' LIMIT 1);
SET @rm7 := (SELECT id FROM products WHERE sku = 'RM-GYS1-003' LIMIT 1);
SET @rm8 := (SELECT id FROM products WHERE sku = 'RM-GYS2-001' LIMIT 1);
SET @rm9 := (SELECT id FROM products WHERE sku = 'RM-GYS2-002' LIMIT 1);
SET @rm10 := (SELECT id FROM products WHERE sku = 'RM-GYS2-003' LIMIT 1);

UPDATE inventory_items ii
JOIN (
  SELECT product_id,
         warehouse_id,
         SUM(CASE WHEN transaction_type = 'IN' THEN -change_quantity ELSE change_quantity END) AS revert_delta
  FROM stock_transaction
  WHERE transaction_no LIKE 'ST-LW2605-%'
  GROUP BY product_id, warehouse_id
) seeded
  ON seeded.product_id = ii.product_id AND seeded.warehouse_id = ii.warehouse_id
SET ii.quantity = GREATEST(0, ii.quantity + seeded.revert_delta),
    ii.updated_at = '2026-05-12 14:30:00',
    ii.lot = CASE WHEN (ii.quantity + seeded.revert_delta) <= 0.0001 THEN NULL ELSE ii.lot END;

DELETE FROM stock_transaction WHERE transaction_no LIKE 'ST-LW2605-%';
DELETE FROM quality_record WHERE batch_id IN (SELECT id FROM batch WHERE batch_no LIKE 'BT-LW2605-%');
DELETE FROM batch WHERE batch_no LIKE 'BT-LW2605-%';
DELETE FROM production_task WHERE task_no LIKE 'TASK-LW2605-%';
DELETE FROM production_material_request_item WHERE request_id IN (SELECT id FROM production_material_request WHERE request_no LIKE 'PMR-LW2605-%');
DELETE FROM production_material_request WHERE request_no LIKE 'PMR-LW2605-%';
DELETE FROM production_plan WHERE plan_no LIKE 'PLAN-SO-LW2605-%';
DELETE FROM sales_record WHERE record_no LIKE 'SR-LW2605-%';
DELETE FROM order_item WHERE order_id IN (SELECT id FROM sales_order WHERE order_no LIKE 'SO-LW2605-%');
DELETE FROM sales_order WHERE order_no LIKE 'SO-LW2605-%';

INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@rm5, @wh_rm, 120.0000, 0.0000, 'RM-OPEN-LW2605-01', '2026-05-04 07:55:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = COALESCE(inventory_items.lot, VALUES(lot));
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMIN-01', @rm5, @wh_rm, 120.0000, 'IN', 'SEED_OPENING', NULL, @warehouse_user_id, '2026-05-04 07:55:00', 'RM-OPEN-LW2605-01', 'Last week raw material opening', @warehouse_user_name);

INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@rm6, @wh_rm, 40.0000, 0.0000, 'RM-OPEN-LW2605-02', '2026-05-04 07:56:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = COALESCE(inventory_items.lot, VALUES(lot));
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMIN-02', @rm6, @wh_rm, 40.0000, 'IN', 'SEED_OPENING', NULL, @warehouse_user_id, '2026-05-04 07:56:00', 'RM-OPEN-LW2605-02', 'Last week raw material opening', @warehouse_user_name);

INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@rm7, @wh_rm, 25.0000, 0.0000, 'RM-OPEN-LW2605-03', '2026-05-04 07:57:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = COALESCE(inventory_items.lot, VALUES(lot));
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMIN-03', @rm7, @wh_rm, 25.0000, 'IN', 'SEED_OPENING', NULL, @warehouse_user_id, '2026-05-04 07:57:00', 'RM-OPEN-LW2605-03', 'Last week raw material opening', @warehouse_user_name);

INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@rm8, @wh_rm, 50.0000, 0.0000, 'RM-OPEN-LW2605-04', '2026-05-04 07:58:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = COALESCE(inventory_items.lot, VALUES(lot));
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMIN-04', @rm8, @wh_rm, 50.0000, 'IN', 'SEED_OPENING', NULL, @warehouse_user_id, '2026-05-04 07:58:00', 'RM-OPEN-LW2605-04', 'Last week raw material opening', @warehouse_user_name);

INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@rm9, @wh_rm, 5.0000, 0.0000, 'RM-OPEN-LW2605-05', '2026-05-04 07:59:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = COALESCE(inventory_items.lot, VALUES(lot));
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMIN-05', @rm9, @wh_rm, 5.0000, 'IN', 'SEED_OPENING', NULL, @warehouse_user_id, '2026-05-04 07:59:00', 'RM-OPEN-LW2605-05', 'Last week raw material opening', @warehouse_user_name);

INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@rm10, @wh_rm, 8.0000, 0.0000, 'RM-OPEN-LW2605-06', '2026-05-04 08:00:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = COALESCE(inventory_items.lot, VALUES(lot));
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMIN-06', @rm10, @wh_rm, 8.0000, 'IN', 'SEED_OPENING', NULL, @warehouse_user_id, '2026-05-04 08:00:00', 'RM-OPEN-LW2605-06', 'Last week raw material opening', @warehouse_user_name);

INSERT INTO sales_order (order_no, customer_id, order_date, status, total_amount, delivery_date, shipping_address, created_by, created_at)
VALUES ('SO-LW2605-001', @customer_id, '2026-05-04 09:00:00', @status_done, 68900.00, '2026-05-06 18:00:00', 'Hangzhou Client Plant A', @sales_user_id, '2026-05-04 09:00:00');
SET @so1 := LAST_INSERT_ID();
INSERT INTO order_item (order_id, product_id, quantity, unit_price, line_total) VALUES
(@so1, @fg1, 8.0000, 6800.0000, 54400.0000),
(@so1, @fg4, 10.0000, 1450.0000, 14500.0000);

INSERT INTO sales_order (order_no, customer_id, order_date, status, total_amount, delivery_date, shipping_address, created_by, created_at)
VALUES ('SO-LW2605-002', @customer_id, '2026-05-06 09:30:00', @status_done, 126000.00, '2026-05-08 18:00:00', 'Hangzhou Client Plant B', @sales_user_id, '2026-05-06 09:30:00');
SET @so2 := LAST_INSERT_ID();
INSERT INTO order_item (order_id, product_id, quantity, unit_price, line_total) VALUES
(@so2, @fg2, 12.0000, 9200.0000, 110400.0000),
(@so2, @fg3, 6.0000, 2600.0000, 15600.0000);

INSERT INTO sales_order (order_no, customer_id, order_date, status, total_amount, delivery_date, shipping_address, created_by, created_at)
VALUES ('SO-LW2605-003', @customer_id, '2026-05-08 10:00:00', @status_done, 56700.00, '2026-05-10 18:30:00', 'Hangzhou Client Plant C', @sales_user_id, '2026-05-08 10:00:00');
SET @so3 := LAST_INSERT_ID();
INSERT INTO order_item (order_id, product_id, quantity, unit_price, line_total) VALUES
(@so3, @fg11, 5.0000, 5300.0000, 26500.0000),
(@so3, @fg12, 4.0000, 6100.0000, 24400.0000),
(@so3, @fg4, 4.0000, 1450.0000, 5800.0000);

INSERT INTO production_plan (plan_no, product_id, planned_quantity, start_date, end_date, status, created_by, created_at, completed_by_email, completed_by_name, completed_by_id, created_by_name)
VALUES ('PLAN-SO-LW2605-001-1-20260504100000', @fg1, 10.0000, '2026-05-04 10:00:00', '2026-05-05 16:30:00', 'WAREHOUSED', @prod_user_1, '2026-05-04 09:50:00', 'sc@qq.com', @prod_user_1_name, @prod_user_1, @prod_user_1_name);
SET @pp1 := LAST_INSERT_ID();
INSERT INTO production_task (task_no, production_plan_id, product_id, scheduled_quantity, started_at, finished_at, status, assigned_to, created_at, source_order_id, source_order_no)
VALUES ('TASK-LW2605-001', @pp1, @fg1, 10.0000, '2026-05-04 10:00:00', '2026-05-05 16:00:00', 'COMPLETED', @prod_user_1, '2026-05-04 09:55:00', @so1, 'SO-LW2605-001');
SET @pt1 := LAST_INSERT_ID();
INSERT INTO production_material_request (request_no, request_note, status, created_by, created_by_email, created_by_name, created_at, warehouse_note, warehouse_reviewed_at, warehouse_reviewed_by, warehouse_reviewed_by_name, materials_issued_at, production_completed_at, warehoused_at, finished_product_id, sales_order_id, procurement_triggered)
VALUES ('PMR-LW2605-001', 'LW seed FG1', @status_warehoused_cn, @prod_user_1, 'sc@qq.com', @prod_user_1_name, '2026-05-04 11:00:00', 'warehouse ok', '2026-05-04 13:00:00', @warehouse_user_id, @warehouse_user_name, '2026-05-04 13:00:00', '2026-05-05 16:30:00', '2026-05-06 09:00:00', @fg1, @so1, b'0');
SET @pmr1 := LAST_INSERT_ID();
INSERT INTO production_material_request_item (request_id, material_product_id, required_quantity, issued_quantity, available_quantity_snapshot, shortage_quantity_snapshot) VALUES
(@pmr1, @rm5, 35.0000, 35.0000, 120.0000, 0.0000),
(@pmr1, @rm9, 1.0000, 1.0000, 5.0000, 0.0000);
UPDATE inventory_items SET quantity = quantity - 35.0000, updated_at = '2026-05-04 13:00:00' WHERE product_id = @rm5 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-01A', @rm5, @wh_rm, 35.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr1, @warehouse_user_id, '2026-05-04 13:00:00', 'RM-OPEN-LW2605-01', 'PMR-LW2605-001 / SO-LW2605-001', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 1.0000, updated_at = '2026-05-04 13:05:00' WHERE product_id = @rm9 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-01B', @rm9, @wh_rm, 1.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr1, @warehouse_user_id, '2026-05-04 13:05:00', 'RM-OPEN-LW2605-05', 'PMR-LW2605-001 / SO-LW2605-001', @warehouse_user_name);
INSERT INTO batch (batch_no, product_id, quantity, manufacture_date, expiry_date, production_task_id, created_at, production_manager_email, production_manager_name, quality_inspected_at, quality_inspector_id, quality_inspector_name, quality_remark, quality_status, source_order_no)
VALUES ('BT-LW2605-001', @fg1, 10.0000, '2026-05-05 16:00:00', NULL, @pt1, '2026-05-05 16:00:00', 'sc@qq.com', @prod_user_1_name, '2026-05-06 08:20:00', @quality_user_id, @quality_user_name, 'PASS', @status_pass, 'SO-LW2605-001');
SET @bt1 := LAST_INSERT_ID();
INSERT INTO quality_record (batch_id, product_id, inspector, inspection_date, result, remarks, inspector_name, notification_sent)
VALUES (@bt1, @fg1, @quality_user_id, '2026-05-06 08:20:00', @status_pass, 'PASS', @quality_user_name, b'0');
INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@fg1, @wh_fg, 10.0000, 0.0000, 'BT-LW2605-001', '2026-05-06 09:00:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = VALUES(lot);
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-FGIN-01', @fg1, @wh_fg, 10.0000, 'IN', 'PRODUCTION_PLAN', @pp1, @warehouse_user_id, '2026-05-06 09:00:00', 'BT-LW2605-001', 'Production stock in seed', @warehouse_user_name);

INSERT INTO production_plan (plan_no, product_id, planned_quantity, start_date, end_date, status, created_by, created_at, completed_by_email, completed_by_name, completed_by_id, created_by_name)
VALUES ('PLAN-SO-LW2605-001-4-20260504103000', @fg4, 18.0000, '2026-05-04 10:30:00', '2026-05-05 17:10:00', 'WAREHOUSED', @prod_user_2, '2026-05-04 10:20:00', 'sc2@qq.com', @prod_user_2_name, @prod_user_2, @prod_user_2_name);
SET @pp2 := LAST_INSERT_ID();
INSERT INTO production_task (task_no, production_plan_id, product_id, scheduled_quantity, started_at, finished_at, status, assigned_to, created_at, source_order_id, source_order_no)
VALUES ('TASK-LW2605-002', @pp2, @fg4, 18.0000, '2026-05-04 10:30:00', '2026-05-05 16:50:00', 'COMPLETED', @prod_user_2, '2026-05-04 10:25:00', @so1, 'SO-LW2605-001');
SET @pt2 := LAST_INSERT_ID();
INSERT INTO production_material_request (request_no, request_note, status, created_by, created_by_email, created_by_name, created_at, warehouse_note, warehouse_reviewed_at, warehouse_reviewed_by, warehouse_reviewed_by_name, materials_issued_at, production_completed_at, warehoused_at, finished_product_id, sales_order_id, procurement_triggered)
VALUES ('PMR-LW2605-002', 'LW seed FG4', @status_warehoused_cn, @prod_user_2, 'sc2@qq.com', @prod_user_2_name, '2026-05-04 11:30:00', 'warehouse ok', '2026-05-04 14:00:00', @warehouse_user_id, @warehouse_user_name, '2026-05-04 14:00:00', '2026-05-05 17:10:00', '2026-05-06 09:15:00', @fg4, @so1, b'0');
SET @pmr2 := LAST_INSERT_ID();
INSERT INTO production_material_request_item (request_id, material_product_id, required_quantity, issued_quantity, available_quantity_snapshot, shortage_quantity_snapshot) VALUES
(@pmr2, @rm8, 22.0000, 22.0000, 50.0000, 0.0000),
(@pmr2, @rm10, 1.0000, 1.0000, 8.0000, 0.0000);
UPDATE inventory_items SET quantity = quantity - 22.0000, updated_at = '2026-05-04 14:00:00' WHERE product_id = @rm8 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-02A', @rm8, @wh_rm, 22.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr2, @warehouse_user_id, '2026-05-04 14:00:00', 'RM-OPEN-LW2605-04', 'PMR-LW2605-002 / SO-LW2605-001', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 1.0000, updated_at = '2026-05-04 14:05:00' WHERE product_id = @rm10 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-02B', @rm10, @wh_rm, 1.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr2, @warehouse_user_id, '2026-05-04 14:05:00', 'RM-OPEN-LW2605-06', 'PMR-LW2605-002 / SO-LW2605-001', @warehouse_user_name);
INSERT INTO batch (batch_no, product_id, quantity, manufacture_date, expiry_date, production_task_id, created_at, production_manager_email, production_manager_name, quality_inspected_at, quality_inspector_id, quality_inspector_name, quality_remark, quality_status, source_order_no)
VALUES ('BT-LW2605-002', @fg4, 18.0000, '2026-05-05 16:50:00', NULL, @pt2, '2026-05-05 16:50:00', 'sc2@qq.com', @prod_user_2_name, '2026-05-06 08:30:00', @quality_user_id, @quality_user_name, 'PASS', @status_pass, 'SO-LW2605-001');
SET @bt2 := LAST_INSERT_ID();
INSERT INTO quality_record (batch_id, product_id, inspector, inspection_date, result, remarks, inspector_name, notification_sent)
VALUES (@bt2, @fg4, @quality_user_id, '2026-05-06 08:30:00', @status_pass, 'PASS', @quality_user_name, b'0');
INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@fg4, @wh_fg, 18.0000, 0.0000, 'BT-LW2605-002', '2026-05-06 09:15:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = VALUES(lot);
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-FGIN-02', @fg4, @wh_fg, 18.0000, 'IN', 'PRODUCTION_PLAN', @pp2, @warehouse_user_id, '2026-05-06 09:15:00', 'BT-LW2605-002', 'Production stock in seed', @warehouse_user_name);

INSERT INTO production_plan (plan_no, product_id, planned_quantity, start_date, end_date, status, created_by, created_at, completed_by_email, completed_by_name, completed_by_id, created_by_name)
VALUES ('PLAN-SO-LW2605-002-2-20260506100000', @fg2, 15.0000, '2026-05-06 10:00:00', '2026-05-07 17:10:00', 'WAREHOUSED', @prod_user_1, '2026-05-06 09:50:00', 'sc@qq.com', @prod_user_1_name, @prod_user_1, @prod_user_1_name);
SET @pp3 := LAST_INSERT_ID();
INSERT INTO production_task (task_no, production_plan_id, product_id, scheduled_quantity, started_at, finished_at, status, assigned_to, created_at, source_order_id, source_order_no)
VALUES ('TASK-LW2605-003', @pp3, @fg2, 15.0000, '2026-05-06 10:00:00', '2026-05-07 16:40:00', 'COMPLETED', @prod_user_1, '2026-05-06 09:55:00', @so2, 'SO-LW2605-002');
SET @pt3 := LAST_INSERT_ID();
INSERT INTO production_material_request (request_no, request_note, status, created_by, created_by_email, created_by_name, created_at, warehouse_note, warehouse_reviewed_at, warehouse_reviewed_by, warehouse_reviewed_by_name, materials_issued_at, production_completed_at, warehoused_at, finished_product_id, sales_order_id, procurement_triggered)
VALUES ('PMR-LW2605-003', 'LW seed FG2', @status_warehoused_cn, @prod_user_1, 'sc@qq.com', @prod_user_1_name, '2026-05-06 11:00:00', 'warehouse ok', '2026-05-06 13:30:00', @warehouse_user_id, @warehouse_user_name, '2026-05-06 13:30:00', '2026-05-07 17:10:00', '2026-05-08 09:10:00', @fg2, @so2, b'0');
SET @pmr3 := LAST_INSERT_ID();
INSERT INTO production_material_request_item (request_id, material_product_id, required_quantity, issued_quantity, available_quantity_snapshot, shortage_quantity_snapshot) VALUES
(@pmr3, @rm5, 40.0000, 40.0000, 85.0000, 0.0000),
(@pmr3, @rm6, 8.0000, 8.0000, 40.0000, 0.0000);
UPDATE inventory_items SET quantity = quantity - 40.0000, updated_at = '2026-05-06 13:30:00' WHERE product_id = @rm5 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-03A', @rm5, @wh_rm, 40.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr3, @warehouse_user_id, '2026-05-06 13:30:00', 'RM-OPEN-LW2605-01', 'PMR-LW2605-003 / SO-LW2605-002', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 8.0000, updated_at = '2026-05-06 13:35:00' WHERE product_id = @rm6 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-03B', @rm6, @wh_rm, 8.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr3, @warehouse_user_id, '2026-05-06 13:35:00', 'RM-OPEN-LW2605-02', 'PMR-LW2605-003 / SO-LW2605-002', @warehouse_user_name);
INSERT INTO batch (batch_no, product_id, quantity, manufacture_date, expiry_date, production_task_id, created_at, production_manager_email, production_manager_name, quality_inspected_at, quality_inspector_id, quality_inspector_name, quality_remark, quality_status, source_order_no)
VALUES ('BT-LW2605-003', @fg2, 15.0000, '2026-05-07 16:40:00', NULL, @pt3, '2026-05-07 16:40:00', 'sc@qq.com', @prod_user_1_name, '2026-05-08 08:20:00', @quality_user_id, @quality_user_name, 'PASS', @status_pass, 'SO-LW2605-002');
SET @bt3 := LAST_INSERT_ID();
INSERT INTO quality_record (batch_id, product_id, inspector, inspection_date, result, remarks, inspector_name, notification_sent)
VALUES (@bt3, @fg2, @quality_user_id, '2026-05-08 08:20:00', @status_pass, 'PASS', @quality_user_name, b'0');
INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@fg2, @wh_fg, 15.0000, 0.0000, 'BT-LW2605-003', '2026-05-08 09:10:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = VALUES(lot);
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-FGIN-03', @fg2, @wh_fg, 15.0000, 'IN', 'PRODUCTION_PLAN', @pp3, @warehouse_user_id, '2026-05-08 09:10:00', 'BT-LW2605-003', 'Production stock in seed', @warehouse_user_name);

INSERT INTO production_plan (plan_no, product_id, planned_quantity, start_date, end_date, status, created_by, created_at, completed_by_email, completed_by_name, completed_by_id, created_by_name)
VALUES ('PLAN-SO-LW2605-002-3-20260506103000', @fg3, 8.0000, '2026-05-06 10:30:00', '2026-05-07 17:30:00', 'WAREHOUSED', @prod_user_2, '2026-05-06 10:20:00', 'sc2@qq.com', @prod_user_2_name, @prod_user_2, @prod_user_2_name);
SET @pp4 := LAST_INSERT_ID();
INSERT INTO production_task (task_no, production_plan_id, product_id, scheduled_quantity, started_at, finished_at, status, assigned_to, created_at, source_order_id, source_order_no)
VALUES ('TASK-LW2605-004', @pp4, @fg3, 8.0000, '2026-05-06 10:30:00', '2026-05-07 17:00:00', 'COMPLETED', @prod_user_2, '2026-05-06 10:25:00', @so2, 'SO-LW2605-002');
SET @pt4 := LAST_INSERT_ID();
INSERT INTO production_material_request (request_no, request_note, status, created_by, created_by_email, created_by_name, created_at, warehouse_note, warehouse_reviewed_at, warehouse_reviewed_by, warehouse_reviewed_by_name, materials_issued_at, production_completed_at, warehoused_at, finished_product_id, sales_order_id, procurement_triggered)
VALUES ('PMR-LW2605-004', 'LW seed FG3', @status_warehoused_cn, @prod_user_2, 'sc2@qq.com', @prod_user_2_name, '2026-05-06 12:00:00', 'warehouse ok', '2026-05-06 14:00:00', @warehouse_user_id, @warehouse_user_name, '2026-05-06 14:00:00', '2026-05-07 17:30:00', '2026-05-08 09:20:00', @fg3, @so2, b'0');
SET @pmr4 := LAST_INSERT_ID();
INSERT INTO production_material_request_item (request_id, material_product_id, required_quantity, issued_quantity, available_quantity_snapshot, shortage_quantity_snapshot) VALUES
(@pmr4, @rm6, 10.0000, 10.0000, 32.0000, 0.0000),
(@pmr4, @rm10, 1.0000, 1.0000, 7.0000, 0.0000);
UPDATE inventory_items SET quantity = quantity - 10.0000, updated_at = '2026-05-06 14:00:00' WHERE product_id = @rm6 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-04A', @rm6, @wh_rm, 10.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr4, @warehouse_user_id, '2026-05-06 14:00:00', 'RM-OPEN-LW2605-02', 'PMR-LW2605-004 / SO-LW2605-002', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 1.0000, updated_at = '2026-05-06 14:05:00' WHERE product_id = @rm10 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-04B', @rm10, @wh_rm, 1.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr4, @warehouse_user_id, '2026-05-06 14:05:00', 'RM-OPEN-LW2605-06', 'PMR-LW2605-004 / SO-LW2605-002', @warehouse_user_name);
INSERT INTO batch (batch_no, product_id, quantity, manufacture_date, expiry_date, production_task_id, created_at, production_manager_email, production_manager_name, quality_inspected_at, quality_inspector_id, quality_inspector_name, quality_remark, quality_status, source_order_no)
VALUES ('BT-LW2605-004', @fg3, 8.0000, '2026-05-07 17:00:00', NULL, @pt4, '2026-05-07 17:00:00', 'sc2@qq.com', @prod_user_2_name, '2026-05-08 08:30:00', @quality_user_id, @quality_user_name, 'PASS', @status_pass, 'SO-LW2605-002');
SET @bt4 := LAST_INSERT_ID();
INSERT INTO quality_record (batch_id, product_id, inspector, inspection_date, result, remarks, inspector_name, notification_sent)
VALUES (@bt4, @fg3, @quality_user_id, '2026-05-08 08:30:00', @status_pass, 'PASS', @quality_user_name, b'0');
INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@fg3, @wh_fg, 8.0000, 0.0000, 'BT-LW2605-004', '2026-05-08 09:20:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = VALUES(lot);
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-FGIN-04', @fg3, @wh_fg, 8.0000, 'IN', 'PRODUCTION_PLAN', @pp4, @warehouse_user_id, '2026-05-08 09:20:00', 'BT-LW2605-004', 'Production stock in seed', @warehouse_user_name);

INSERT INTO production_plan (plan_no, product_id, planned_quantity, start_date, end_date, status, created_by, created_at, completed_by_email, completed_by_name, completed_by_id, created_by_name)
VALUES ('PLAN-SO-LW2605-003-13-20260508103000', @fg11, 6.0000, '2026-05-08 10:30:00', '2026-05-09 16:20:00', 'WAREHOUSED', @prod_user_1, '2026-05-08 10:20:00', 'sc@qq.com', @prod_user_1_name, @prod_user_1, @prod_user_1_name);
SET @pp5 := LAST_INSERT_ID();
INSERT INTO production_task (task_no, production_plan_id, product_id, scheduled_quantity, started_at, finished_at, status, assigned_to, created_at, source_order_id, source_order_no)
VALUES ('TASK-LW2605-005', @pp5, @fg11, 6.0000, '2026-05-08 10:30:00', '2026-05-09 16:00:00', 'COMPLETED', @prod_user_1, '2026-05-08 10:25:00', @so3, 'SO-LW2605-003');
SET @pt5 := LAST_INSERT_ID();
INSERT INTO production_material_request (request_no, request_note, status, created_by, created_by_email, created_by_name, created_at, warehouse_note, warehouse_reviewed_at, warehouse_reviewed_by, warehouse_reviewed_by_name, materials_issued_at, production_completed_at, warehoused_at, finished_product_id, sales_order_id, procurement_triggered)
VALUES ('PMR-LW2605-005', 'LW seed FG11', @status_warehoused_cn, @prod_user_1, 'sc@qq.com', @prod_user_1_name, '2026-05-08 12:00:00', 'warehouse ok', '2026-05-08 14:00:00', @warehouse_user_id, @warehouse_user_name, '2026-05-08 14:00:00', '2026-05-09 16:20:00', '2026-05-10 10:00:00', @fg11, @so3, b'0');
SET @pmr5 := LAST_INSERT_ID();
INSERT INTO production_material_request_item (request_id, material_product_id, required_quantity, issued_quantity, available_quantity_snapshot, shortage_quantity_snapshot) VALUES
(@pmr5, @rm7, 12.0000, 12.0000, 25.0000, 0.0000),
(@pmr5, @rm9, 1.0000, 1.0000, 4.0000, 0.0000);
UPDATE inventory_items SET quantity = quantity - 12.0000, updated_at = '2026-05-08 14:00:00' WHERE product_id = @rm7 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-05A', @rm7, @wh_rm, 12.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr5, @warehouse_user_id, '2026-05-08 14:00:00', 'RM-OPEN-LW2605-03', 'PMR-LW2605-005 / SO-LW2605-003', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 1.0000, updated_at = '2026-05-08 14:05:00' WHERE product_id = @rm9 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-05B', @rm9, @wh_rm, 1.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr5, @warehouse_user_id, '2026-05-08 14:05:00', 'RM-OPEN-LW2605-05', 'PMR-LW2605-005 / SO-LW2605-003', @warehouse_user_name);
INSERT INTO batch (batch_no, product_id, quantity, manufacture_date, expiry_date, production_task_id, created_at, production_manager_email, production_manager_name, quality_inspected_at, quality_inspector_id, quality_inspector_name, quality_remark, quality_status, source_order_no)
VALUES ('BT-LW2605-005', @fg11, 6.0000, '2026-05-09 16:00:00', NULL, @pt5, '2026-05-09 16:00:00', 'sc@qq.com', @prod_user_1_name, '2026-05-10 09:10:00', @quality_user_id, @quality_user_name, 'PASS', @status_pass, 'SO-LW2605-003');
SET @bt5 := LAST_INSERT_ID();
INSERT INTO quality_record (batch_id, product_id, inspector, inspection_date, result, remarks, inspector_name, notification_sent)
VALUES (@bt5, @fg11, @quality_user_id, '2026-05-10 09:10:00', @status_pass, 'PASS', @quality_user_name, b'0');
INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@fg11, @wh_fg, 6.0000, 0.0000, 'BT-LW2605-005', '2026-05-10 10:00:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = VALUES(lot);
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-FGIN-05', @fg11, @wh_fg, 6.0000, 'IN', 'PRODUCTION_PLAN', @pp5, @warehouse_user_id, '2026-05-10 10:00:00', 'BT-LW2605-005', 'Production stock in seed', @warehouse_user_name);

INSERT INTO production_plan (plan_no, product_id, planned_quantity, start_date, end_date, status, created_by, created_at, completed_by_email, completed_by_name, completed_by_id, created_by_name)
VALUES ('PLAN-SO-LW2605-003-14-20260508110000', @fg12, 5.0000, '2026-05-08 11:00:00', '2026-05-09 17:00:00', 'WAREHOUSED', @prod_user_2, '2026-05-08 10:50:00', 'sc2@qq.com', @prod_user_2_name, @prod_user_2, @prod_user_2_name);
SET @pp6 := LAST_INSERT_ID();
INSERT INTO production_task (task_no, production_plan_id, product_id, scheduled_quantity, started_at, finished_at, status, assigned_to, created_at, source_order_id, source_order_no)
VALUES ('TASK-LW2605-006', @pp6, @fg12, 5.0000, '2026-05-08 11:00:00', '2026-05-09 16:40:00', 'COMPLETED', @prod_user_2, '2026-05-08 10:55:00', @so3, 'SO-LW2605-003');
SET @pt6 := LAST_INSERT_ID();
INSERT INTO production_material_request (request_no, request_note, status, created_by, created_by_email, created_by_name, created_at, warehouse_note, warehouse_reviewed_at, warehouse_reviewed_by, warehouse_reviewed_by_name, materials_issued_at, production_completed_at, warehoused_at, finished_product_id, sales_order_id, procurement_triggered)
VALUES ('PMR-LW2605-006', 'LW seed FG12', @status_warehoused_cn, @prod_user_2, 'sc2@qq.com', @prod_user_2_name, '2026-05-08 12:30:00', 'warehouse ok', '2026-05-08 14:30:00', @warehouse_user_id, @warehouse_user_name, '2026-05-08 14:30:00', '2026-05-09 17:00:00', '2026-05-10 10:10:00', @fg12, @so3, b'0');
SET @pmr6 := LAST_INSERT_ID();
INSERT INTO production_material_request_item (request_id, material_product_id, required_quantity, issued_quantity, available_quantity_snapshot, shortage_quantity_snapshot) VALUES
(@pmr6, @rm8, 9.0000, 9.0000, 28.0000, 0.0000),
(@pmr6, @rm10, 1.0000, 1.0000, 6.0000, 0.0000);
UPDATE inventory_items SET quantity = quantity - 9.0000, updated_at = '2026-05-08 14:30:00' WHERE product_id = @rm8 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-06A', @rm8, @wh_rm, 9.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr6, @warehouse_user_id, '2026-05-08 14:30:00', 'RM-OPEN-LW2605-04', 'PMR-LW2605-006 / SO-LW2605-003', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 1.0000, updated_at = '2026-05-08 14:35:00' WHERE product_id = @rm10 AND warehouse_id = @wh_rm;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-RMOUT-06B', @rm10, @wh_rm, 1.0000, 'OUT', 'PRODUCTION_MATERIAL_REQUEST', @pmr6, @warehouse_user_id, '2026-05-08 14:35:00', 'RM-OPEN-LW2605-06', 'PMR-LW2605-006 / SO-LW2605-003', @warehouse_user_name);
INSERT INTO batch (batch_no, product_id, quantity, manufacture_date, expiry_date, production_task_id, created_at, production_manager_email, production_manager_name, quality_inspected_at, quality_inspector_id, quality_inspector_name, quality_remark, quality_status, source_order_no)
VALUES ('BT-LW2605-006', @fg12, 5.0000, '2026-05-09 16:40:00', NULL, @pt6, '2026-05-09 16:40:00', 'sc2@qq.com', @prod_user_2_name, '2026-05-10 09:20:00', @quality_user_id, @quality_user_name, 'PASS', @status_pass, 'SO-LW2605-003');
SET @bt6 := LAST_INSERT_ID();
INSERT INTO quality_record (batch_id, product_id, inspector, inspection_date, result, remarks, inspector_name, notification_sent)
VALUES (@bt6, @fg12, @quality_user_id, '2026-05-10 09:20:00', @status_pass, 'PASS', @quality_user_name, b'0');
INSERT INTO inventory_items (product_id, warehouse_id, quantity, reserved_quantity, lot, updated_at)
VALUES (@fg12, @wh_fg, 5.0000, 0.0000, 'BT-LW2605-006', '2026-05-10 10:10:00')
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), updated_at = VALUES(updated_at), lot = VALUES(lot);
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-FGIN-06', @fg12, @wh_fg, 5.0000, 'IN', 'PRODUCTION_PLAN', @pp6, @warehouse_user_id, '2026-05-10 10:10:00', 'BT-LW2605-006', 'Production stock in seed', @warehouse_user_name);

UPDATE inventory_items SET quantity = quantity - 8.0000, updated_at = '2026-05-06 15:00:00' WHERE product_id = @fg1 AND warehouse_id = @wh_fg;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-SALE-01A', @fg1, @wh_fg, 8.0000, 'OUT', 'SALES_ORDER', @so1, @warehouse_user_id, '2026-05-06 15:00:00', 'BT-LW2605-001', 'Last week sales shipment', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 10.0000, updated_at = '2026-05-06 15:05:00' WHERE product_id = @fg4 AND warehouse_id = @wh_fg;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-SALE-01B', @fg4, @wh_fg, 10.0000, 'OUT', 'SALES_ORDER', @so1, @warehouse_user_id, '2026-05-06 15:05:00', 'BT-LW2605-002', 'Last week sales shipment', @warehouse_user_name);
INSERT INTO sales_record (record_no, order_id, order_no, total_amount, customer_name, shipping_address, status, created_by, created_by_name, created_at)
VALUES ('SR-LW2605-001', @so1, 'SO-LW2605-001', 68900.00, @customer_name, 'Hangzhou Client Plant A', @status_done, @sales_user_id, @sales_user_name, '2026-05-06 18:20:00');

UPDATE inventory_items SET quantity = quantity - 12.0000, updated_at = '2026-05-08 15:00:00' WHERE product_id = @fg2 AND warehouse_id = @wh_fg;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-SALE-02A', @fg2, @wh_fg, 12.0000, 'OUT', 'SALES_ORDER', @so2, @warehouse_user_id, '2026-05-08 15:00:00', 'BT-LW2605-003', 'Last week sales shipment', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 6.0000, updated_at = '2026-05-08 15:05:00' WHERE product_id = @fg3 AND warehouse_id = @wh_fg;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-SALE-02B', @fg3, @wh_fg, 6.0000, 'OUT', 'SALES_ORDER', @so2, @warehouse_user_id, '2026-05-08 15:05:00', 'BT-LW2605-004', 'Last week sales shipment', @warehouse_user_name);
INSERT INTO sales_record (record_no, order_id, order_no, total_amount, customer_name, shipping_address, status, created_by, created_by_name, created_at)
VALUES ('SR-LW2605-002', @so2, 'SO-LW2605-002', 126000.00, @customer_name, 'Hangzhou Client Plant B', @status_done, @sales_user_id, @sales_user_name, '2026-05-08 18:10:00');

UPDATE inventory_items SET quantity = quantity - 5.0000, updated_at = '2026-05-10 15:00:00' WHERE product_id = @fg11 AND warehouse_id = @wh_fg;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-SALE-03A', @fg11, @wh_fg, 5.0000, 'OUT', 'SALES_ORDER', @so3, @warehouse_user_id, '2026-05-10 15:00:00', 'BT-LW2605-005', 'Last week sales shipment', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 4.0000, updated_at = '2026-05-10 15:05:00' WHERE product_id = @fg12 AND warehouse_id = @wh_fg;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-SALE-03B', @fg12, @wh_fg, 4.0000, 'OUT', 'SALES_ORDER', @so3, @warehouse_user_id, '2026-05-10 15:05:00', 'BT-LW2605-006', 'Last week sales shipment', @warehouse_user_name);
UPDATE inventory_items SET quantity = quantity - 4.0000, updated_at = '2026-05-10 15:10:00' WHERE product_id = @fg4 AND warehouse_id = @wh_fg;
INSERT INTO stock_transaction (transaction_no, product_id, warehouse_id, change_quantity, transaction_type, related_type, related_id, created_by, created_at, lot, remark, created_by_name)
VALUES ('ST-LW2605-SALE-03C', @fg4, @wh_fg, 4.0000, 'OUT', 'SALES_ORDER', @so3, @warehouse_user_id, '2026-05-10 15:10:00', 'BT-LW2605-002', 'Last week sales shipment', @warehouse_user_name);
INSERT INTO sales_record (record_no, order_id, order_no, total_amount, customer_name, shipping_address, status, created_by, created_by_name, created_at)
VALUES ('SR-LW2605-003', @so3, 'SO-LW2605-003', 56700.00, @customer_name, 'Hangzhou Client Plant C', @status_done, @sales_user_id, @sales_user_name, '2026-05-10 18:00:00');

COMMIT;

