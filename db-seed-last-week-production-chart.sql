-- 为生产记录页柱状图准备“上一个周”的测试生产数据。
-- 统计口径：2026-04-27 ~ 2026-05-03 已完成的生产记录。
-- 目标：覆盖 6 个成品产品、2 个生产管理员，便于验证分页、搜索、tooltip 与全员统计。

START TRANSACTION;

SET @prod_role_id := (SELECT id FROM roles WHERE name = 'ROLE_PRODUCTION_MANAGER' LIMIT 1);
SET @prod_user_1 := (SELECT id FROM users WHERE email = 'sc@qq.com' LIMIT 1);

INSERT INTO users (username, password, full_name, email, phone, enabled, created_at, updated_at)
SELECT 'sc2', (SELECT password FROM users WHERE id = @prod_user_1), 'sc2', 'sc2@qq.com', '13800000002', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'sc2@qq.com');

SET @prod_user_2 := (SELECT id FROM users WHERE email = 'sc2@qq.com' LIMIT 1);

INSERT INTO user_roles (user_id, role_id)
SELECT @prod_user_2, @prod_role_id
WHERE @prod_user_2 IS NOT NULL
  AND @prod_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @prod_user_2 AND role_id = @prod_role_id);

SET @p1 := (SELECT id FROM products WHERE sku = 'FG-XS-001' LIMIT 1);
SET @p2 := (SELECT id FROM products WHERE sku = 'FG-XS-002' LIMIT 1);
SET @p3 := (SELECT id FROM products WHERE sku = 'FG-XS-003' LIMIT 1);
SET @p4 := (SELECT id FROM products WHERE sku = 'FG-XS-004' LIMIT 1);
SET @p11 := (SELECT id FROM products WHERE sku = 'FG-TEST-011' LIMIT 1);
SET @p12 := (SELECT id FROM products WHERE sku = 'FG-TEST-012' LIMIT 1);

DELETE FROM batch WHERE production_task_id IN (SELECT id FROM production_task WHERE production_plan_id IN (SELECT id FROM production_plan WHERE plan_no LIKE 'PLAN-LASTWK-%'));
DELETE FROM production_task WHERE production_plan_id IN (SELECT id FROM production_plan WHERE plan_no LIKE 'PLAN-LASTWK-%');
DELETE FROM production_plan WHERE plan_no LIKE 'PLAN-LASTWK-%';

INSERT INTO production_plan (
  plan_no, product_id, planned_quantity, start_date, end_date, status,
  created_by, created_at, completed_by_email, completed_by_name, completed_by_id, created_by_name
) VALUES
('PLAN-LASTWK-001', @p1, 18.00, '2026-04-27 08:30:00', '2026-04-28 17:40:00', 'DONE',
 @prod_user_1, '2026-04-27 08:30:00', 'sc@qq.com', 'sc', @prod_user_1, 'sc'),
('PLAN-LASTWK-002', @p2, 32.00, '2026-04-28 09:10:00', '2026-04-29 18:00:00', 'WAREHOUSED',
 @prod_user_1, '2026-04-28 09:10:00', 'sc@qq.com', 'sc', @prod_user_1, 'sc'),
('PLAN-LASTWK-003', @p3, 14.00, '2026-04-29 07:50:00', '2026-04-30 16:35:00', 'DONE',
 @prod_user_2, '2026-04-29 07:50:00', 'sc2@qq.com', 'sc2', @prod_user_2, 'sc2'),
('PLAN-LASTWK-004', @p4, 26.00, '2026-04-30 08:20:00', '2026-05-01 19:10:00', 'WAREHOUSED',
 @prod_user_2, '2026-04-30 08:20:00', 'sc2@qq.com', 'sc2', @prod_user_2, 'sc2'),
('PLAN-LASTWK-005', @p11, 12.00, '2026-05-01 08:00:00', '2026-05-02 17:20:00', 'DONE',
 @prod_user_1, '2026-05-01 08:00:00', 'sc@qq.com', 'sc', @prod_user_1, 'sc'),
('PLAN-LASTWK-006', @p12, 9.00, '2026-05-02 09:00:00', '2026-05-03 15:45:00', 'WAREHOUSED',
 @prod_user_2, '2026-05-02 09:00:00', 'sc2@qq.com', 'sc2', @prod_user_2, 'sc2');

COMMIT;


