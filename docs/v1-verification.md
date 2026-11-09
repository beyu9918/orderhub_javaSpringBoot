# V1 实现与验证记录

日期：2026-11-08（Asia/Shanghai）。本次验证使用隔离 Compose 项目 `orderhub-v1-verify`，未更改现有 `.env` 或原业务数据卷。

## 已执行

| 检查 | 结果 |
| --- | --- |
| Maven 编译、测试、打包 | 成功生成可执行 JAR |
| 旧版兼容与缓存单元测试 | 10 个通过，0 失败、0 错误 |
| 真实 PostgreSQL / Redis 的 V1 集成测试 | 14 个通过，0 失败、0 错误、0 跳过 |
| Flyway 空库迁移 | V1 → V2 → V3 → V4 成功 |
| 带旧数据迁移 | 独立 schema 先执行 V1、录入旧数据，再迁移；快照与预占余额正确，原表保留 |
| 容器业务冒烟 | 管理员登录、SPU/SKU、地址、购物车、下单、幂等、支付、发货、送达、收货、取消、退款通过 |
| 容器缓存 | 商品 / SKU 缓存与 1800 秒 TTL、编辑后失效通过 |
| Redis 故障降级 | 停止隔离 Redis，商品仍从数据库正确返回；随后恢复 Redis |
| 容器 Excel 与图片 | XLSX 下载、非 root 用户上传 / 读取 / 删除 PNG 通过 |
| WebSocket | 真实 HTTP 升级、浏览器子协议、用户与管理员权限、用户隔离、事务回滚不推送、注销后不推送通过 |

集成测试文件：`src/test/java/orderhub/v1/V1CommerceIT.java`。覆盖最后一件库存并发竞争、并发幂等下单与取消、库存不足整单回滚、优惠券并发限领、百分比舍入、默认地址唯一、地址快照、订单价格快照、未付款超时取消、七天自动完成、优惠券过期、退款拒绝及撤销恢复原状态、多包裹全部送达、库存版本冲突、可选字段清空、分类循环校验、JWT 注销 / 改密码 / 禁用、报表和图片。

## 容器构建限制与替代方式

标准多阶段 Dockerfile 构建尝试被 Docker Hub 的网络故障阻断：auth.docker.io 获取认证 token 超时。没有修改 Docker 的全局网络设置。

为完成容器运行验证，新增 `Dockerfile.jar`，将已通过测试的 JAR 放入本机已有、包含 Java 21 的 `orderhub-verify-app:latest` 镜像中。实际执行：

```powershell
docker build --pull=false --build-arg RUNTIME_IMAGE=orderhub-verify-app:latest -t orderhub-v1-verify-app -f Dockerfile.jar .
docker compose -p orderhub-v1-verify --env-file .local/verification.env -f compose.yaml -f .local/compose.verify.yaml up --no-build -d
```

因此本次已验证 JAR 运行镜像及 Compose 编排；标准多阶段构建仍需 Docker Hub 网络恢复后执行，未将该失败宣称为通过。

## 当前本地入口

- Swagger：<http://localhost:18080/swagger-ui.html>
- 管理员登录：`POST /api/v1/admin/auth/login`，字段 account / password。
- 验证凭据：Git 忽略的 `.local/verification.env`，不是用户根目录 `.env`。
- PostgreSQL 15432、Redis 16379；业务演示库 orderhub，集成测试库 orderhub_v1_test；测试缓存使用 Redis DB 1。
- 演示库保留冒烟生成的账号、分类、已完成 / 已取消 / 已退款订单，测试商品已下架。图片测试文件已通过 API 删除。

停止验证环境并保留卷：

```powershell
docker compose -p orderhub-v1-verify --env-file .local/verification.env -f compose.yaml -f .local/compose.verify.yaml down
```

默认 8080 的正常部署仍按 README，使用根目录 `.env`。不需要把验证凭据复制到正常部署。

## V1 的具体约定

- 支付、退款和物流均为模拟；全额退款审批通过即完成，complete 接口可幂等重试。
- 运费为零，折扣按服务端规则计算；不实现发货后的退货退款。
- WebSocket 为单实例在线通知，不提供持久消息队列与离线重放。
- 缓存最终一致，资金、库存、鉴权和幂等依据数据库。
- 图片为本地持久卷，不依赖云对象存储。
- 所有表结构、迁移和端点可从 [数据库文档](database.md)、[HTTP 示例](requests.http) 与 Swagger 核对；[原始需求](v1-requirements.md) 保留完整范围。
