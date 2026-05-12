-- 为销售记录页柱状图准备“上一个周”的测试销售数据。
-- 设计目标：
-- 1) 使用上周日期（2026-04-27 ~ 2026-05-03）
-- 2) 覆盖至少 6 个成品产品，验证分页与搜索
-- 3) 订单、订单明细、销售记录保持闭环一致

START TRANSACTION;

-- 补充两个用于图表测试的成品产品，避免现有成品不足 5 个导致无法翻页测试。
INSERT INTO products (
  sku, name, product_type, material_category, specification, preferred_supplier,
  origin, description, unit, weight, unit_price, safety_stock, lead_time_days, created_at
)
SELECT 'FG-TEST-011', '智能低压配电柜', 'FINISHED_GOOD', NULL, 'XL-21 / 800A', NULL,
       '杭州', '用于销售柱状图测试的附加成品', '台', 0, 5300, 5, 7, '2026-04-20 09:00:00'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'FG-TEST-011');

INSERT INTO products (
  sku, name, product_type, material_category, specification, preferred_supplier,
  origin, description, unit, weight, unit_price, safety_stock, lead_time_days, created_at
)
SELECT 'FG-TEST-012', '户外防雨机柜', 'FINISHED_GOOD', NULL, 'IP55 / 壁挂式', NULL,
       '宁波', '用于销售柱状图测试的附加成品', '台', 0, 6100, 5, 7, '2026-04-20 09:05:00'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'FG-TEST-012');

SET @customer_id := (SELECT id FROM customers ORDER BY id LIMIT 1);
SET @sales_user_id := (SELECT u.id FROM users u
  JOIN user_roles ur ON ur.user_id = u.id
  JOIN roles r ON r.id = ur.role_id
  WHERE r.name = 'ROLE_SALES_MANAGER'
  ORDER BY u.id
  LIMIT 1);
SET @sales_user_name := (SELECT COALESCE(full_name, username) FROM users WHERE id = @sales_user_id);

SET @p1 := (SELECT id FROM products WHERE sku = 'FG-XS-001' LIMIT 1);
SET @p2 := (SELECT id FROM products WHERE sku = 'FG-XS-002' LIMIT 1);
SET @p3 := (SELECT id FROM products WHERE sku = 'FG-XS-003' LIMIT 1);
SET @p4 := (SELECT id FROM products WHERE sku = 'FG-XS-004' LIMIT 1);
SET @p11 := (SELECT id FROM products WHERE sku = 'FG-TEST-011' LIMIT 1);
SET @p12 := (SELECT id FROM products WHERE sku = 'FG-TEST-012' LIMIT 1);

-- 若之前已经插入过同批测试订单，则先清掉再重建，保证脚本可重复执行。
DELETE FROM sales_record WHERE order_no LIKE 'SO-LASTWK-%';
DELETE FROM order_item WHERE order_id IN (SELECT id FROM sales_order WHERE order_no LIKE 'SO-LASTWK-%');
DELETE FROM sales_order WHERE order_no LIKE 'SO-LASTWK-%';

-- 订单 1：覆盖产品 1、4
INSERT INTO sales_order (order_no, customer_id, order_date, status, total_amount, delivery_date, shipping_address, created_by, created_at)
VALUES ('SO-LASTWK-001', @customer_id, '2026-04-28 10:15:00', '已完成', 110600.00, '2026-05-03 10:00:00', '浙江省杭州市滨江区测试路 18 号', @sales_user_id, '2026-04-28 10:15:00');
SET @so1 := LAST_INSERT_ID();
INSERT INTO order_item (order_id, product_id, quantity, unit_price, line_total) VALUES
(@so1, @p1, 12.00, 6800.00, 81600.00),
(@so1, @p4, 20.00, 1450.00, 29000.00);
INSERT INTO sales_record (record_no, order_id, order_no, total_amount, customer_name, shipping_address, status, created_by, created_by_name, created_at)
VALUES ('SR-LASTWK-001', @so1, 'SO-LASTWK-001', 110600.00, 'gk', '浙江省杭州市滨江区测试路 18 号', '已完成', @sales_user_id, @sales_user_name, '2026-04-28 18:20:00');

-- 订单 2：覆盖产品 2、3
INSERT INTO sales_order (order_no, customer_id, order_date, status, total_amount, delivery_date, shipping_address, created_by, created_at)
VALUES ('SO-LASTWK-002', @customer_id, '2026-04-30 09:35:00', '已完成', 304400.00, '2026-05-04 16:00:00', '浙江省杭州市滨江区测试路 18 号', @sales_user_id, '2026-04-30 09:35:00');
SET @so2 := LAST_INSERT_ID();
INSERT INTO order_item (order_id, product_id, quantity, unit_price, line_total) VALUES
(@so2, @p2, 28.00, 9200.00, 257600.00),
(@so2, @p3, 18.00, 2600.00, 46800.00);
INSERT INTO sales_record (record_no, order_id, order_no, total_amount, customer_name, shipping_address, status, created_by, created_by_name, created_at)
VALUES ('SR-LASTWK-002', @so2, 'SO-LASTWK-002', 304400.00, 'gk', '浙江省杭州市滨江区测试路 18 号', '已完成', @sales_user_id, @sales_user_name, '2026-04-30 19:05:00');

-- 订单 3：覆盖产品 4、11、12，形成 6 个唯一成品产品用于分页测试。
INSERT INTO sales_order (order_no, customer_id, order_date, status, total_amount, delivery_date, shipping_address, created_by, created_at)
VALUES ('SO-LASTWK-003', @customer_id, '2026-05-02 14:10:00', '已完成', 155650.00, '2026-05-06 11:00:00', '浙江省杭州市滨江区测试路 18 号', @sales_user_id, '2026-05-02 14:10:00');
SET @so3 := LAST_INSERT_ID();
INSERT INTO order_item (order_id, product_id, quantity, unit_price, line_total) VALUES
(@so3, @p4, 15.00, 1450.00, 21750.00),
(@so3, @p11, 8.00, 5300.00, 42400.00),
(@so3, @p12, 15.00, 6100.00, 91500.00);
INSERT INTO sales_record (record_no, order_id, order_no, total_amount, customer_name, shipping_address, status, created_by, created_by_name, created_at)
VALUES ('SR-LASTWK-003', @so3, 'SO-LASTWK-003', 155650.00, 'gk', '浙江省杭州市滨江区测试路 18 号', '已完成', @sales_user_id, @sales_user_name, '2026-05-02 20:15:00');

COMMIT;

