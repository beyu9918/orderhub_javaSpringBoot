# 需求对应实现

| 需求 | 实现位置（src/main/java/orderhub/v1 下） |
| --- | --- |
| 18 核心表、JSONB、索引、约束 | resources/db/migration/V2__commerce_v1.sql、V4__multiple_shipments.sql；common/JsonbHandler |
| 旧数据升级与 identity 重置 | src/main/java/db/migration/V3__Import_legacy_data.java |
| 注册、双端登录、注销、资料与改密码 | auth、config/V1SecurityConfig |
| 用户与管理员维护、禁用、保留管理员 | user/AccountController、auth/AuthService |
| 地址 CRUD 与唯一默认 | address |
| 树形分类、分类循环校验 | category、product/CatalogService |
| SPU/SKU、上架下架、价格查询 | product |
| 可售/预占库存、版本校验、库存流水 | inventory |
| 购物车累加、数量替换、勾选结算 | cart |
| 订单快照、事务、请求指纹、幂等 | order/OrderService |
| 状态机与状态历史、再次购买 | order |
| 多次 MOCK 支付尝试、回调幂等 | payment、order/OrderService |
| 多包裹、全部送达后确认收货 | shipment、order/OrderService |
| 全额退款、拒绝与撤销恢复原状态 | refund、order/OrderService |
| 优惠券领取、金额折扣、百分比折扣 | coupon |
| 管理员操作与前后快照审计 | audit |
| Dashboard、趋势、Top SKU、低库存 | report |
| Excel 与日期区间报表 | report/ReportService |
| 图片格式检查、重编码、持久化、删除 | common/ImageController |
| Cache Aside、ZSET、登录记录、幂等缓存 | common/CommerceCache 及对应业务 Service |
| 用户/管理端订单通知、握手鉴权 | websocket、order/OrderEvent |
| 30 分钟取消、7 天完成、优惠券过期 | task/OrderTasks、coupon/CouponService |
| REST 文档与 JWT Authorize | config/OpenApiConfig（原包）、各模块 Controller |
| PostgreSQL / Redis / 应用 / 图片卷 | compose.yaml、Dockerfile、Dockerfile.jar |

与原文中可选或未明确规则对应的 V1 约定，见 [验证记录](v1-verification.md) 和 README 的业务规则。
