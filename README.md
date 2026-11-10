# OrderHub 电商订单与履约系统 V1

面向用户与运营管理端的 B2C 后端。Java 21 / Spring Boot 4.1.1 / Spring Security JWT / MyBatis-Plus / PostgreSQL / Redis / WebSocket / OpenAPI / Apache POI / Docker Compose。

实现 18 张核心表及完整业务链：注册登录 → 地址 → 分类与 SPU/SKU → 购物车勾选 → 事务预占库存下单 → MOCK 支付 → 管理处理 → 多包裹发货 → 全部送达 → 确认收货。另有未付款超时取消、未发货退款、优惠券、审计、图片上传、Dashboard 和 Excel 报表。

## 启动

需要 Docker Desktop Linux containers / Docker Engine + Compose。本机 Java 运行时使用 JDK 21。

已有 .env 可继续使用。没有时执行：

```powershell
Copy-Item .env.example .env
```

编辑 .env：DB_PASSWORD 为数据库密码，JWT_SECRET 为至少 32 字节的随机签名密钥，ADMIN_USERNAME / ADMIN_PASSWORD 为首次创建的管理员账号密码，ADMIN_EMAIL 为邮箱。不要提交真实密钥到 Git。

```powershell
docker compose up --build -d
docker compose logs -f app
```

- Swagger：<http://localhost:8080/swagger-ui.html>
- OpenAPI：<http://localhost:8080/v3/api-docs>
- 进程存活检查：<http://localhost:8080/api/health>
- PostgreSQL / Redis 仅映射到本机 5432 / 6379；容器间通过服务名互通。

数据库、Redis、图片保存在命名卷。docker compose down 保留数据。更换 JWT_SECRET 后执行 docker compose up -d --force-recreate app，已有 token 失效。已有数据库密码、管理员密码不会因为修改 .env 自动变更。

本机 Java 运行时，先启动 docker compose up -d postgres redis，在终端设置 DB_PASSWORD、JWT_SECRET、ADMIN_USERNAME、ADMIN_PASSWORD，再执行 .\mvnw.cmd spring-boot:run。Java 不会自动加载 .env。

## 从旧版升级

Flyway 追加 V2、V3、V4，不改原 V1。旧六表数据保留，原库存表改名 legacy_inventory；新系统使用 18 张核心表。V3 将旧数据一次性导入新表并重设 identity 起点，旧商品变成一 SPU 一 SKU，旧 CREATED 订单映射 PENDING_PAYMENT，其已扣库存映射为预占库存。

旧账号保留用户名与 BCrypt 密码，使用 /api/v1/.../auth/login 重新登录；邮箱暂为 legacy-<id>@legacy.invalid。旧订单保存原名称、价格与金额，未采集的收货地址不凭空补造。升级后的旧待付款订单也受 30 分钟超时取消规则约束。

新版默认关闭旧 /api/auth、/api/products、/api/cart、/api/orders 接口，避免旧鉴权绕开新注销与禁用规则。旧代码和测试保留，开发对照时可单独设置 LEGACY_API_ENABLED=true。两套模型只在升级时导入一次，后续不双写，不要混用两套接口录入业务。

## 手工测试

完整请求示例见 [docs/requests.http](docs/requests.http)。Swagger 对应接口点击 Try it out，按次序执行并记录真实 ID：

1. POST /api/v1/admin/auth/login，请求体 {"account":"admin","password":"你的管理员密码"}。Authorize 只填返回的 accessToken，不填 JWT_SECRET。
2. 管理员创建分类 → 创建 SPU（DRAFT）→ 创建 SKU 及 initialStock → publish 商品。
3. 注册普通用户并登录，Authorize 换为用户 token；创建收货地址。
4. 按 skuId 添加购物车。修改、删除、勾选操作使用 itemId；重复 POST 累加数量，PUT 替换数量。
5. 创建订单填写 addressId / 可选 userCouponId，添加 Idempotency-Key 请求头；只结算勾选条目。
6. 创建支付尝试（method=MOCK）→ mock-success，检查订单 PAID。
7. 换管理员 token：process → ship，记录 shipmentId → 更新物流 DELIVERED。
8. 换用户 token：confirm-receipt，检查订单 COMPLETED、预占库存清零，查看状态历史、库存流水、审计和报表。

异常分支：另建待付款订单再取消；另建已付款但未发货订单申请退款，再由管理员 approve / reject。发货后不支持直接退款。重复支付、取消、退款完成不会重复变更余额。

## API 范围

用户前缀 /api/v1/user，管理员前缀 /api/v1/admin，后者除登录外要求 ADMIN。完整 HTTP 方法和参数以 Swagger 为准。

| 用户模块 | 路径 |
| --- | --- |
| 认证资料 | /auth/register、/auth/login、/auth/logout、/profile、/profile/password |
| 地址 | /addresses、/addresses/{id}、/addresses/{id}/default |
| 浏览 | /categories、/products、/products/{id}、/products/{id}/skus |
| 购物车 | /cart、/cart/items、/cart/items/{itemId}、/cart/items/{itemId}/select、/cart/select-all |
| 订单 | /orders、/orders/{orderId} 及 cancel / confirm-receipt / buy-again |
| 支付 | /orders/{orderId}/payments、/payments/{paymentId}/mock-success、/mock-fail |
| 物流 | /orders/{orderId}/shipment，返回包裹列表 |
| 售后 | /orders/{orderId}/refunds、/refunds/{refundId}、/refunds/{refundId}/cancel |
| 优惠券 | /coupons/available、/coupons/{couponId}/claim、/my-coupons |

| 管理模块 | 路径 |
| --- | --- |
| 账号 | /auth/login、/auth/logout、/users、/admins 及 enable / disable |
| 分类 | /categories 及 enable / disable |
| 商品 SKU | /products、/products/{id} 及 publish / unpublish；/products/{productId}/skus、/skus/{skuId} |
| 库存 | /inventory、/inventory/low-stock、/inventory/{skuId} 及 adjust / logs |
| 订单物流 | /orders、/orders/{id} 及 process / ship / cancel / status-history / shipments；/shipments/{id}/status |
| 退款 | /refunds、/refunds/{id} 及 approve / reject / complete |
| 优惠券 | /coupons 及 enable / disable |
| Dashboard | /dashboard/overview、/sales-trend、/order-trend、/order-status、/top-products、/low-stock |
| 报表 | /reports/sales、/orders、/products、/export |
| 图片 | POST /files/images（multipart 字段 file）、DELETE /files/{id} |
| 审计 | /audit-logs、/audit-logs/{id} |

商品筛选支持 keyword、categoryId、minPrice、maxPrice、page、size、sort；sort 为 newest / oldest / name / priceAsc / priceDesc。订单日期为带时区 ISO 时间；报表日期为 YYYY-MM-DD，按 UTC 自然日，默认最近 7 天、最多 366 天。分页每页最多 100 条。

## 业务规则

- 订单状态由 order/OrderService 统一控制：PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED → COMPLETED；未付款可 CANCELLED。PAID / PROCESSING 可 REFUND_REQUESTED → REFUNDED，拒绝或撤销恢复原状态。
- V1 全额 MOCK 退款在管理员同意时立即完成，refund.status=COMPLETED，complete 提供幂等重试。不接真实支付、退款、物流网关。
- 运费统一为零。优惠券 FIXED 为减免金额，PERCENTAGE 为减免百分比，10 表示减免 10%。优惠不超过商品总额。已领取优惠券不能修改规则，只能启用/停用。
- initialStock 仅创建 SKU 使用，编辑 SKU 不改库存。库存调整提交 quantity 增量、expectedVersion、reason；库存始终非负，保留可售与预占余额流水。
- 每用户每种券只能领一次。库存预占、订单快照、状态日志、优惠券、购物车清理同事务。幂等键保存请求体指纹，新购买意图必须换键。
- 每分钟处理一批最多 200 个过期订单或待完成订单：30 分钟未付款取消、全部包裹送达 7 天自动完成；AVAILABLE 券到期转 EXPIRED。
- JWT 含账号 token_version，注销、改密码使该账号全部旧 token 失效，禁用立即拒绝旧 token。数据库为鉴权真相源，Redis 故障不影响撤销校验。密码不出现在响应或审计里。
- 图片仅 PNG / JPEG，最大 5 MB、4096×4096；重编码为 PNG，以随机 UUID 命名，保存在本地卷。公开读取 /api/v1/images/{id}。

## Redis 与 WebSocket

商品详情缓存增加空值缓存（60–75 秒）、互斥重建与随机 TTL（30–35 分钟），等待重建最多 2 秒；SKU 列表继续缓存 30 分钟。管理修改提交后失效，结算直接读并锁数据库。Dashboard dashboard:overview TTL 1 分钟，订单变更提交后失效。product:hot ZSET 统计访问，auth:login:{userId} 记录登录版本与时间，order:idempotency:{userId}:{key} 缓存订单 ID 1 天。

限量优惠券领取增加 Redisson 用户级分布式锁，以及 Lua 原子检查活动、名额、重复领取并预扣；随后同步提交 PostgreSQL 事务。数据库失败补偿，进程中断的预扣由恢复任务或用户重试核对。Redis 故障仍走原数据库兜底，库存方案保持数据库事务与版本控制。配置、失败路径、Redis key 和面试讲解见 [Redis 并发设计](docs/redis-concurrency.md)。

Redis 不可用时回退数据库。幂等仍有唯一约束和用户行锁保障。缓存为最终一致性，并发回填或失效失败时可保留至 TTL，不用于库存与结算判断。

WebSocket 用户 /ws/orders（另有 /api/v1/user/ws/orders 别名），管理员 /ws/admin/orders。只在事务提交后推送，用户只接收本人订单，管理端要求 ADMIN。支持 Authorization Bearer 握手；浏览器用子协议携带 token：

```javascript
const socket = new WebSocket('ws://localhost:8080/ws/orders', [
  'orderhub', 'bearer.' + accessToken
]);
socket.onmessage = event => console.log(JSON.parse(event.data));
```

事件：ORDER_PAID / ORDER_PROCESSING / ORDER_SHIPPED / ORDER_DELIVERED / ORDER_COMPLETED / ORDER_CANCELLED / REFUND_APPROVED / REFUND_REJECTED。发送前重新校验 token。ALLOWED_ORIGINS 默认 localhost:8080 / 18080，可配置浏览器来源。发送字符串 ping，返回字符串 pong；不要把 token 放进 URL。

通知是单实例在线推送，无离线重放；多实例分发、真实支付、退货物流属于后续扩展。HTTP 查询和数据库日志是可靠状态来源。

## 自动测试

```powershell
.\mvnw.cmd test
```

执行旧版兼容测试和缓存单元测试，H2 PostgreSQL 模式不需要外部服务。新版 JSONB、锁、状态机、WebSocket、迁移和 Excel 使用真实 PostgreSQL / Redis：

```powershell
# 提前创建专用测试库，库名必须以 _test 结尾。
$env:V1_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/orderhub_v1_test'
$env:V1_TEST_DB_USERNAME = 'orderhub'
$env:V1_TEST_DB_PASSWORD = '测试数据库密码'
$env:V1_TEST_REDIS_PORT = '6379'
.\mvnw.cmd -Pv1-integration verify
```

V1 测试清空专用测试库的核心表，并在 Redis DB 1 清理测试缓存；不要连接业务库。没有合法 V1_TEST_DB_URL 时新版集成测试跳过，不应将跳过视为验证成功。

代码按业务位于 orderhub.v1 的 auth、user、address、category、product、inventory、cart、order、payment、shipment、refund、coupon、report、audit、websocket、task 模块。原代码保留在原包。

运行中服务可使用 `scripts/smoke-test.ps1` 做完整购买、取消和退款冒烟，需在终端设置 ADMIN_USERNAME / ADMIN_PASSWORD。脚本会创建测试数据并最终下架测试商品。

若 Docker Hub 暂不可用，可以先用 Maven 打包，再以本机已有 Java 21 镜像构建运行镜像。示例中的基础镜像必须在本机存在：

```powershell
.\mvnw.cmd package
docker build --pull=false --build-arg RUNTIME_IMAGE=orderhub-verify-app:latest -t orderhub-app -f Dockerfile.jar .
docker compose up --no-build -d
```

标准多阶段构建与本次运行镜像验证的区别，以及当前隔离演示入口，见 [V1 验证记录](docs/v1-verification.md)。需求对应代码见 [覆盖清单](docs/v1-coverage.md)。

设计与范围：[数据库 ER](docs/database.md)、[原始需求](docs/v1-requirements.md)。
