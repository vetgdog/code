# SteelOps Supply-Chain System

这是一个已具备前后端联调能力的供应链协同系统，包含：

- Spring Boot 2.7 后端（`src/main/java`）
- Vue 3 + Vite 前端（`frontend/`）
- JWT 登录鉴权与基于角色的访问控制
- 销售、生产、采购、库存、质检、客户追溯等核心流程
- WebSocket / STOMP 实时消息推送
- 生产周计划、采购周计划的自动生成能力

## 技术栈

- Java 17
- Maven
- Spring Boot / Spring Security / Spring Data JPA
- MySQL
- Vue 3 / Vite / Axios / SockJS / STOMP

## 已验证的本地默认配置

当前仓库中的默认配置来自 `src/main/resources/application.yml`：

- 后端端口：`8085`
- 数据库：`jdbc:mysql://localhost:3306/code`
- WebSocket 端点：`/ws`
- API 基础路径：`/api/v1`

如需在本地运行，请先根据你的环境修改数据库账号密码与 JWT 密钥。

## 后端启动

1. 创建 MySQL 数据库 `code`
2. 按需修改 `src/main/resources/application.yml`
3. 运行测试并启动服务

```powershell
mvn test
mvn spring-boot:run
```

或先打包再运行：

```powershell
mvn -DskipTests package
java -jar target/code-1.0-SNAPSHOT.jar
```

## 前端启动

```powershell
Set-Location .\frontend
npm install
npm run dev
```

前端默认通过 Vite 代理访问后端。更多前端环境变量说明见 `frontend/README.md`。

## 主要功能

### 1. 认证与账号

- `POST /api/v1/auth/register`：注册账号
- `POST /api/v1/auth/login`：登录并获取 JWT
- `POST /api/v1/profile/change-password`：修改当前账号密码

登录后请在请求头中携带：

```text
Authorization: Bearer <jwt>
```

### 2. 销售与订单流程

- 销售订单创建、查询、状态流转
- 订单关联生产计划与仓库流程
- 销售记录查询与导出

### 3. 生产模块

- 生产任务创建与状态更新
- 生产记录查询
- 质检预警查看
- 生产周计划列表、当前计划查看、手动重新生成

相关接口示例：

- `GET /api/v1/production/weekly-plans`
- `GET /api/v1/production/weekly-plans/current`
- `POST /api/v1/production/weekly-plans/generate`

### 4. 采购模块

- 采购申请与采购单流程
- 供应商接单、发货、通知仓库入库
- 采购记录导出（CSV / Excel）
- 原材料档案维护与 Excel 导入
- 采购周计划列表、当前计划查看、手动重新生成

相关接口示例：

- `GET /api/v1/procurement/weekly-plans`
- `GET /api/v1/procurement/weekly-plans/current`
- `POST /api/v1/procurement/weekly-plans/generate`

### 5. 库存与质检

- 成品 / 原材料库存查询
- 入库、出库、库存预警处理
- 批次质检记录与客户质量追溯

## 角色示例

系统中已使用的角色包括：

- `ROLE_ADMIN`
- `ROLE_CUSTOMER`
- `ROLE_SUPPLIER`
- `ROLE_SALES_MANAGER`
- `ROLE_PROCUREMENT_MANAGER`
- `ROLE_PRODUCTION_MANAGER`
- `ROLE_WAREHOUSE_MANAGER`
- `ROLE_QUALITY_INSPECTOR`

调用注册接口时，可按业务需要传入对应角色标识；系统内部统一以 `ROLE_*` 形式保存。

## 实时消息

系统启用了 SockJS + STOMP。

- 连接端点：`/ws`
- 已使用的主题包括：`/topic/orders`、`/topic/production`、`/topic/procurement`、`/topic/quality`、`/topic/mrp`

## 当前仓库状态

从现有测试与构建结果看，项目已不是“仅有脚手架”的状态：

- 后端测试可通过
- 前端可成功构建
- 周计划相关接口已有后端测试与联调产物

如果你要继续扩展功能，建议优先参考：

- `src/main/java/com/code/controller`
- `src/main/java/com/code/service`
- `frontend/src/pages`
- `frontend/src/api/services.js`

