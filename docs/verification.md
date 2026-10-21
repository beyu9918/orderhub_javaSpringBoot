# 验证记录

验证日期：2026-10-22（Australia/Sydney）。以下记录来自本次本地执行，不代表生产压测结果。

| 验证 | 结果 |
| --- | --- |
| Java 21 / Maven Wrapper 编译与 package | 通过，生成可执行 JAR |
| H2 PostgreSQL 兼容模式 | 10 个测试，0 失败、0 错误 |
| PostgreSQL 17.11 独立测试库 | 同一套 10 个测试，0 失败、0 错误 |
| Docker 多阶段镜像构建与 Compose 启动 | app、postgres、redis 均运行；数据库与 Redis 健康检查通过 |
| 容器 HTTP 冒烟 | 登录、商品、购物车、下单、重试、库存、重复取消、OpenAPI 均通过 |
| 真实 Redis | JSON 缓存写入、60 秒 TTL、更新后删除、读取新值均通过 |
| Redis 停机降级 | 停止隔离验证环境 Redis 后，商品详情仍可从数据库返回；随后恢复 Redis |
| Flyway | PostgreSQL 空库成功执行 V1，重启后正常校验 |
| 联合索引执行计划 | 各 50,000 条模拟商品与订单，查询使用预期联合索引 |

集成测试覆盖：BCrypt 密码存储、注册校验、重复用户名、登录失败、JWT 过期、角色权限、订单归属、分页边界、订单名称与价格快照、库存不足整单回滚、并发竞争最后一件库存、并发重复下单、并发重复取消、数据库约束和 OpenAPI 文档。

索引验证使用 [explain.sql](explain.sql)，在专用测试库中插入模拟数据并于事务结束回滚。实际观察到：

```text
商品分类分页：Index Scan Backward using idx_product_active_category_id
订单状态分页：Index Scan using idx_order_user_status_created_id
```

此验证确认特定查询在模拟数据下使用了索引，没有进行有索引与无索引的对比压测，也不据此推导吞吐量或百分比性能提升。

本次隔离环境使用 Compose 项目 `orderhub-verify`，端口为 app 18080、PostgreSQL 15432、Redis 16379；随机凭据只保存在 Git 忽略的 `.local/verification.env` 中。日常启动依照 README 使用 `.env` 和默认端口即可。需要停止隔离环境时执行：

```powershell
docker compose -p orderhub-verify --env-file .local/verification.env -f compose.yaml -f .local/compose.verify.yaml down
```

该命令保留验证环境数据卷。测试用户、已取消订单和下架商品保留在隔离演示库；专用测试库为 `orderhub_test`。
