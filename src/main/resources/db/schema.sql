-- Comprehensive schema for stainless steel supply-chain collaboration system

-- Users and roles
-- 系统用户表
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 用户ID
  `username` VARCHAR(100) NOT NULL, -- 用户名（允许重名）
  `password` VARCHAR(255) NOT NULL, -- 密码（加密存储）
  `full_name` VARCHAR(200), -- 全名
  `email` VARCHAR(200) NOT NULL UNIQUE, -- 邮箱（账号唯一标识）
  `phone` VARCHAR(50), -- 电话
  `enabled` TINYINT(1) DEFAULT 1, -- 是否启用
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, -- 创建时间
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP -- 更新时间
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色表
CREATE TABLE IF NOT EXISTS `roles` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 角色ID
  `name` VARCHAR(100) NOT NULL UNIQUE, -- 角色名称
  `description` VARCHAR(255) -- 角色描述
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_roles` (
  `user_id` BIGINT NOT NULL, -- 用户ID
  `role_id` BIGINT NOT NULL, -- 角色ID
  PRIMARY KEY (`user_id`, `role_id`) -- 联合主键
  -- 外键约束：fk_userroles_user (user_id -> users.id)
  -- 外键约束：fk_userroles_role (role_id -> roles.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Business master tables
-- 客户表
CREATE TABLE IF NOT EXISTS `customers` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 客户ID
  `code` VARCHAR(64) NOT NULL UNIQUE, -- 客户编码
  `name` VARCHAR(200) NOT NULL, -- 客户名称
  `contact` VARCHAR(200), -- 联系人
  `phone` VARCHAR(50), -- 联系电话
  `email` VARCHAR(200), -- 邮箱
  `address` VARCHAR(500), -- 地址
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 供应商表
CREATE TABLE IF NOT EXISTS `suppliers` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 供应商ID
  `code` VARCHAR(64) NOT NULL UNIQUE, -- 供应商编码
  `name` VARCHAR(200) NOT NULL, -- 供应商名称
  `contact` VARCHAR(200), -- 联系人
  `phone` VARCHAR(50), -- 联系电话
  `email` VARCHAR(200), -- 邮箱
  `address` VARCHAR(500), -- 地址
  `lead_time_days` INT DEFAULT 0, -- 交货周期（天）
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 仓库表
CREATE TABLE IF NOT EXISTS `warehouses` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 仓库ID
  `code` VARCHAR(64) NOT NULL UNIQUE, -- 仓库编码
  `name` VARCHAR(200) NOT NULL, -- 仓库名称
  `location` VARCHAR(255), -- 仓库位置
  `manager` BIGINT, -- 仓库管理员ID
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 产品表
CREATE TABLE IF NOT EXISTS `products` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 产品ID
  `sku` VARCHAR(64) NOT NULL UNIQUE, -- 产品SKU
  `name` VARCHAR(255) NOT NULL, -- 产品名称
  `description` TEXT, -- 产品描述
  `unit` VARCHAR(32), -- 单位
  `weight` DECIMAL(18,4), -- 重量
  `unit_price` DECIMAL(18,4) DEFAULT 0, -- 默认单价
  `default_warehouse_id` BIGINT, -- 默认仓库ID
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_products_default_wh (default_warehouse_id -> warehouses.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- BOM (Bill of Materials)
-- 物料清单表
CREATE TABLE IF NOT EXISTS `bom` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- BOM ID
  `product_id` BIGINT NOT NULL, -- 产品ID
  `version` VARCHAR(64), -- BOM版本
  `quantity` DECIMAL(18,4) DEFAULT 1, -- 数量
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_bom_product (product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- BOM子项表
CREATE TABLE IF NOT EXISTS `bom_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- BOM子项ID
  `bom_id` BIGINT NOT NULL, -- BOM ID
  `component_product_id` BIGINT NOT NULL, -- 组件产品ID
  `quantity` DECIMAL(18,4) NOT NULL, -- 数量
  `unit` VARCHAR(32), -- 单位
  `notes` TEXT -- 备注
  -- 外键约束：fk_bomitem_bom (bom_id -> bom.id)
  -- 外键约束：fk_bomitem_component (component_product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Sales orders
-- 销售订单表
CREATE TABLE IF NOT EXISTS `sales_order` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 订单ID
  `order_no` VARCHAR(100) NOT NULL UNIQUE, -- 订单编号
  `customer_id` BIGINT, -- 客户ID
  `order_date` DATETIME DEFAULT CURRENT_TIMESTAMP, -- 订单日期
  `status` VARCHAR(50) DEFAULT 'NEW', -- 订单状态
  `total_amount` DECIMAL(18,2) DEFAULT 0, -- 总金额
  `delivery_date` DATETIME, -- 交货日期
  `shipping_address` VARCHAR(500), -- 收货地址
  `created_by` BIGINT, -- 创建人ID
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_salesorder_customer (customer_id -> customers.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 销售记录表
CREATE TABLE IF NOT EXISTS `sales_record` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_no` VARCHAR(100) NOT NULL UNIQUE,
  `order_id` BIGINT NOT NULL,
  `order_no` VARCHAR(100) NOT NULL,
  `total_amount` DECIMAL(18,2) DEFAULT 0,
  `customer_name` VARCHAR(200),
  `shipping_address` VARCHAR(500),
  `status` VARCHAR(50),
  `created_by` BIGINT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单子项表
CREATE TABLE IF NOT EXISTS `order_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 订单子项ID
  `order_id` BIGINT NOT NULL, -- 订单ID
  `product_id` BIGINT NOT NULL, -- 产品ID
  `quantity` DECIMAL(18,4) NOT NULL, -- 数量
  `unit_price` DECIMAL(18,4), -- 单价
  `line_total` DECIMAL(18,4) -- 行总金额
  -- 外键约束：fk_orderitem_order (order_id -> sales_order.id)
  -- 外键约束：fk_orderitem_product (product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Production planning & tasks
-- 生产计划表
CREATE TABLE IF NOT EXISTS `production_plan` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 计划ID
  `plan_no` VARCHAR(100) NOT NULL UNIQUE, -- 计划编号
  `product_id` BIGINT NOT NULL, -- 产品ID
  `planned_quantity` DECIMAL(18,4) NOT NULL, -- 计划数量
  `start_date` DATETIME, -- 开始日期
  `end_date` DATETIME, -- 结束日期
  `status` VARCHAR(50) DEFAULT 'PLANNED', -- 计划状态
  `created_by` BIGINT, -- 创建人ID
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_productionplan_product (product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 生产任务表
CREATE TABLE IF NOT EXISTS `production_task` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 任务ID
  `task_no` VARCHAR(100) NOT NULL UNIQUE, -- 任务编号
  `production_plan_id` BIGINT, -- 生产计划ID
  `product_id` BIGINT, -- 产品ID
  `scheduled_quantity` DECIMAL(18,4), -- 计划数量
  `started_at` DATETIME, -- 开始时间
  `finished_at` DATETIME, -- 完成时间
  `status` VARCHAR(50) DEFAULT 'PENDING', -- 任务状态
  `assigned_to` BIGINT, -- 负责人ID
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_task_plan (production_plan_id -> production_plan.id)
  -- 外键约束：fk_task_product (product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MRP requirements
-- MRP需求表
CREATE TABLE IF NOT EXISTS `mrp_requirement` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 需求ID
  `product_id` BIGINT NOT NULL, -- 产品ID
  `required_quantity` DECIMAL(18,4) NOT NULL, -- 需求数量
  `required_date` DATETIME, -- 需求日期
  `source_type` VARCHAR(50), -- 需求来源类型
  `related_id` BIGINT, -- 相关ID
  `status` VARCHAR(50) DEFAULT 'OPEN', -- 需求状态
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_mrp_product (product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Purchase / procurement
-- 采购申请表
CREATE TABLE IF NOT EXISTS `purchase_request` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 申请表ID
  `request_no` VARCHAR(100) NOT NULL UNIQUE, -- 申请编号
  `requested_by` BIGINT, -- 申请人ID
  `supplier_id` BIGINT, -- 供应商ID
  `request_date` DATETIME DEFAULT CURRENT_TIMESTAMP, -- 申请日期
  `status` VARCHAR(50) DEFAULT 'OPEN', -- 申请状态
  `notes` TEXT -- 备注
  -- 外键约束：fk_pr_supplier (supplier_id -> suppliers.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 采购订单表
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 订单ID
  `po_no` VARCHAR(100) NOT NULL UNIQUE, -- 订单编号
  `supplier_id` BIGINT, -- 供应商ID
  `order_date` DATETIME DEFAULT CURRENT_TIMESTAMP, -- 订单日期
  `status` VARCHAR(50) DEFAULT 'CREATED', -- 订单状态
  `total_amount` DECIMAL(18,2) DEFAULT 0, -- 总金额
  `created_by` BIGINT -- 创建人ID
  -- 外键约束：fk_po_supplier (supplier_id -> suppliers.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 采购订单子项表
CREATE TABLE IF NOT EXISTS `purchase_order_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 订单子项ID
  `purchase_order_id` BIGINT NOT NULL, -- 采购订单ID
  `product_id` BIGINT NOT NULL, -- 产品ID
  `quantity` DECIMAL(18,4) NOT NULL, -- 数量
  `unit_price` DECIMAL(18,4), -- 单价
  `line_total` DECIMAL(18,4) -- 行总金额
  -- 外键约束：fk_poi_po (purchase_order_id -> purchase_order.id)
  -- 外键约束：fk_poi_product (product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Inventory and stock movements
-- 库存表
CREATE TABLE IF NOT EXISTS `inventory_items` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 库存ID
  `product_id` BIGINT NOT NULL, -- 产品ID
  `warehouse_id` BIGINT NOT NULL, -- 仓库ID
  `quantity` DECIMAL(18,4) DEFAULT 0, -- 库存量
  `reserved_quantity` DECIMAL(18,4) DEFAULT 0, -- 预留量
  `lot` VARCHAR(100), -- 批次号
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- 更新时间
  UNIQUE KEY `ux_product_warehouse` (`product_id`, `warehouse_id`) -- 产品和仓库的唯一约束
  -- 外键约束：fk_inv_product (product_id -> products.id)
  -- 外键约束：fk_inv_warehouse (warehouse_id -> warehouses.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 库存交易表
CREATE TABLE IF NOT EXISTS `stock_transaction` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 交易ID
  `transaction_no` VARCHAR(100) NOT NULL UNIQUE, -- 交易编号
  `product_id` BIGINT NOT NULL, -- 产品ID
  `warehouse_id` BIGINT, -- 仓库ID
  `change_quantity` DECIMAL(18,4) NOT NULL, -- 数量变化
  `transaction_type` VARCHAR(50) NOT NULL, -- 交易类型
  `lot` VARCHAR(100), -- 批次号
  `related_type` VARCHAR(50), -- 相关类型
  `related_id` BIGINT, -- 相关ID
  `remark` VARCHAR(500), -- 备注
  `created_by` BIGINT, -- 创建人ID
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_st_product (product_id -> products.id)
  -- 外键约束：fk_st_warehouse (warehouse_id -> warehouses.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Batches and quality
-- 批次表
CREATE TABLE IF NOT EXISTS `batch` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 批次ID
  `batch_no` VARCHAR(100) NOT NULL UNIQUE, -- 批次号
  `product_id` BIGINT, -- 产品ID
  `quantity` DECIMAL(18,4), -- 数量
  `manufacture_date` DATETIME, -- 生产日期
  `expiry_date` DATETIME, -- 过期日期
  `production_task_id` BIGINT, -- 生产任务ID
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP -- 创建时间
  -- 外键约束：fk_batch_product (product_id -> products.id)
  -- 外键约束：fk_batch_task (production_task_id -> production_task.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 质量记录表
CREATE TABLE IF NOT EXISTS `quality_record` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY, -- 质量记录ID
  `batch_id` BIGINT, -- 批次ID
  `product_id` BIGINT, -- 产品ID
  `inspector` BIGINT, -- 检验员ID
  `inspection_date` DATETIME DEFAULT CURRENT_TIMESTAMP, -- 检验日期
  `result` VARCHAR(50), -- 检验结果
  `remarks` TEXT -- 备注
  -- 外键约束：fk_qr_batch (batch_id -> batch.id)
  -- 外键约束：fk_qr_product (product_id -> products.id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Indexes for performance
CREATE INDEX idx_sales_order_customer ON `sales_order`(`customer_id`); -- 销售订单客户索引
CREATE INDEX idx_order_item_product ON `order_item`(`product_id`); -- 订单子项产品索引
CREATE INDEX idx_inventory_product_warehouse ON `inventory_items`(`product_id`, `warehouse_id`); -- 库存产品仓库索引