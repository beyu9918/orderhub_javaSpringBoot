# OrderHub 电商订单管理系统

Java 21 / Spring Boot 4.1.1 / PostgreSQL / MyBatis-Plus / Redis / Spring Security JWT / Docker Compose / OpenAPI。

已实现用户注册与登录、管理员商品和库存管理、购物车、事务下单、历史订单分页、订单明细快照和取消归还库存。项目定位为个人后端练习，订单状态为 CREATED / CANCELLED，尚无支付、物流和退款集成。

## 快速运行

需要 Docker Desktop（Linux containers）或 Docker Engine + Compose。

```powershell
Copy-Item .env.example .env
# 编辑 .env，替换数据库密码、JWT_SECRET（至少 32 字节）和管理员密码
docker compose up --build -d
docker compose logs -f app
```

启动时 Flyway 自动迁移数据库。首次启动根据 ADMIN_USERNAME / ADMIN_PASSWORD 创建管理员，已有管理员不会被重设密码；若用户名已属于普通用户则启动失败，避免意外提权。注册接口始终创建 USER。

- 健康检查：<http://localhost:8080/api/health>（进程存活，不代表依赖健康）
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>

PostgreSQL 和 Redis 只映射到本机，容器间使用服务名通信。数据保存在命名卷，`docker compose down` 保留数据。`.env` 已加入 Git 忽略，不要提交真实密钥。此 Compose 面向本地演示；公网部署应配置 HTTPS 和网络访问控制。

## 本机 Java 运行

先准备 PostgreSQL 和 Redis，也可只启动 Compose 的基础服务：

```powershell
docker compose up -d postgres redis
$env:DB_PASSWORD = '与 .env 中保持一致'
$env:JWT_SECRET = '替换为至少32字节的随机密钥'
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = '替换为管理员密码'
.\mvnw.cmd spring-boot:run
```

Maven Wrapper 会自动获取 Maven。macOS / Linux 使用 `./mvnw`。其他可选环境变量：DB_URL、DB_USERNAME、REDIS_HOST、REDIS_PORT、REDIS_PASSWORD、CACHE_ENABLED。启动本机 Java 时 `.env` 不会自动加载，需要设置环境变量。

## API

| 方法 | 路径 | 权限与行为 |
| --- | --- | --- |
| POST | /api/auth/register | 公开，注册 USER |
| POST | /api/auth/login | 公开，返回 JWT，有效期 2 小时 |
| GET | /api/auth/me | 当前用户 |
| GET | /api/products | 公开，page / size / category / keyword |
| GET | /api/products/{id} | 公开，上架商品详情 |
| GET / POST | /api/admin/products | ADMIN，查询全部 / 创建 |
| PUT / DELETE | /api/admin/products/{id} | ADMIN，更新商品与库存 / 下架 |
| GET / PUT | /api/admin/products/{id}/stock | ADMIN，读取 / 设置实时库存 |
| GET / DELETE | /api/cart/items | 当前用户，查询 / 清空 |
| PUT / DELETE | /api/cart/items/{productId} | 设置数量 / 删除 |
| POST | /api/orders | 从当前购物车下单，请求体带 requestKey |
| GET | /api/orders | 当前用户历史订单，page / size / status |
| GET | /api/orders/{id} | 当前用户订单详情 |
| POST | /api/orders/{id}/cancel | 取消并归还库存，幂等 |

登录后在 Swagger 的 Authorize 输入 accessToken，或请求头传 `Authorization: Bearer <accessToken>`。成功结果直接返回对象或分页结构 `{page,size,total,records}`；校验错误 400、未认证 401、权限不足 403、不存在或非本人资源 404、冲突或库存不足 409。业务错误使用 ProblemDetail；Security 认证错误由框架返回 401 / 403。

请求示例见 [docs/requests.http](docs/requests.http)，可使用 IDE HTTP Client 顺序执行。所有金额单位为人民币元，服务端根据数据库价格计算总额，客户端不传金额。

## 一致性与缓存

- 下单使用数据库事务、商品行锁及条件库存扣减，任一商品失败时整单回滚，保留购物车。
- requestKey 在同一用户范围唯一；重复提交返回原订单，即使购物车已变化或订单已取消。新的购买意图必须使用新键；幂等键不允许复用。数据库唯一约束作为最终保障。
- 订单保存购买时的名称、单价与小计，商品改名、改价、下架不影响历史。
- Redis 缓存公开商品详情，TTL 60 秒；商品编辑或下架在事务提交后删除缓存。Redis 故障时降级查数据库。列表、库存及订单结算直接读数据库，避免使用缓存价格下单。
- 缓存为最终一致性：并发读写或失效失败时，旧详情最多保留一个 TTL；不承诺管理操作后所有读取即时刷新。
- JWT 使用 HS256 签名、发行者与过期时间校验，密码使用 BCrypt。无 token 撤销机制，角色改变需等待 token 到期；更换签名密钥会使已有 token 失效。

数据库 ER 图、索引和 EXPLAIN 示例见 [docs/database.md](docs/database.md)。依赖版本按官方 [MyBatis-Plus Boot 4 安装说明](https://baomidou.com/en/getting-started/) 和 [springdoc 文档](https://springdoc.org/) 选择。

## 测试

本次实际构建、数据库、缓存与容器验证结果见 [docs/verification.md](docs/verification.md)。

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

默认使用 H2 PostgreSQL 兼容模式，执行完整 Spring 上下文和 HTTP 集成测试，禁用 Redis 以便不依赖外部服务。覆盖注册登录、过期 token、角色权限、订单归属、分页参数、订单快照、库存不足回滚、并发防超卖、重复下单、重复取消和 OpenAPI。

使用独立的 PostgreSQL **测试数据库**重复验证（测试会清空业务表，勿连接开发或生产数据库）：

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/orderhub_test'
$env:TEST_DB_USERNAME = 'orderhub'
$env:TEST_DB_PASSWORD = '测试数据库密码'
.\mvnw.cmd test
```

测试配置支持同一套用例切换数据库；H2 不能替代 PostgreSQL 的实际执行计划及部署验证。Redis 故障降级与提交后失效另有单元测试。

运行中的服务也可执行冒烟测试（会创建测试用户、商品及已取消订单；商品最终下架）：

```powershell
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = '与部署时保持一致'
.\scripts\smoke-test.ps1
```

## 目录

```text
src/main/java/orderhub/
  api/          REST 接口、校验 DTO、响应和异常处理
  config/       Security、OpenAPI、MyBatis-Plus、管理员初始化
  domain/       数据库实体
  mapper/       MyBatis-Plus CRUD 与加锁/库存 SQL
  service/      事务业务与 Redis 缓存
src/main/resources/db/migration/  Flyway DDL
docs/           ER 图、SQL 分析与 HTTP 请求示例
```
