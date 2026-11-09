# OrderHub 电商订单管理系统 V1

**定位：** 面向用户端与运营管理端的 B2C 电商订单与履约系统。  
**核心技术：** Java / Spring Boot / Spring Security / JWT / MyBatis-Plus / PostgreSQL / Redis / WebSocket / OpenAPI / Docker

---

# 一、数据库 ER 设计

V1 共设计 **18 张核心业务表**：

| # | 表名 | 作用 |
|---|---|---|
| 1 | `users` | 用户及管理员账号 |
| 2 | `addresses` | 用户收货地址 |
| 3 | `categories` | 商品分类 |
| 4 | `products` | SPU 商品 |
| 5 | `product_skus` | SKU 商品规格 |
| 6 | `inventory` | 当前库存 |
| 7 | `inventory_logs` | 库存变更流水 |
| 8 | `carts` | 用户购物车 |
| 9 | `cart_items` | 购物车商品 |
| 10 | `orders` | 订单主表 |
| 11 | `order_items` | 订单商品快照 |
| 12 | `order_status_logs` | 订单状态流转记录 |
| 13 | `payments` | 支付记录 |
| 14 | `shipments` | 发货/物流记录 |
| 15 | `refunds` | 退款/售后 |
| 16 | `coupons` | 优惠券 |
| 17 | `user_coupons` | 用户领取的优惠券 |
| 18 | `audit_logs` | 管理员操作审计 |

---

# 二、ER 关系

核心关系：

```text
users
 ├── 1:N addresses
 ├── 1:1 carts
 ├── 1:N orders
 ├── 1:N user_coupons
 └── 1:N refunds

categories
 └── 1:N products

products
 └── 1:N product_skus

product_skus
 ├── 1:1 inventory
 ├── 1:N inventory_logs
 ├── 1:N cart_items
 └── 1:N order_items

carts
 └── 1:N cart_items

orders
 ├── 1:N order_items
 ├── 1:N order_status_logs
 ├── 1:N payments
 ├── 1:N shipments
 └── 1:N refunds

coupons
 └── 1:N user_coupons

user_coupons
 └── 0..1 : 1 orders

admins(users)
 └── 1:N audit_logs
```

---

# 三、18 张表详细设计

## 1. users

```text
id                  BIGINT PK
email               VARCHAR UNIQUE
username            VARCHAR
password_hash       VARCHAR
phone               VARCHAR
role                USER / ADMIN
status              ACTIVE / DISABLED
created_at
updated_at
```

约束：

```text
email UNIQUE
role NOT NULL
```

---

## 2. addresses

```text
id
user_id             FK -> users.id
receiver_name
phone
province
city
district
detail_address
postal_code
is_default
created_at
updated_at
```

关系：

```text
一个用户可以有多个地址
```

业务规则：

> 同一用户最多只有一个 `is_default = true`。

---

## 3. categories

```text
id
name
parent_id           nullable
sort_order
status              ACTIVE / INACTIVE
created_at
updated_at
```

支持：

```text
Electronics
 ├── Phone
 ├── Laptop
 └── Headphones
```

---

## 4. products

SPU 层。

```text
id
category_id         FK
name
description
brand
image_url
status              DRAFT / ACTIVE / INACTIVE
created_at
updated_at
```

这里不要直接保存库存。

---

## 5. product_skus

SKU 层。

例如：

```text
iPhone 17
├── Black / 256 GB
├── Black / 512 GB
└── White / 256 GB
```

字段：

```text
id
product_id          FK
sku_code            UNIQUE
attributes          JSONB
price
status              ACTIVE / INACTIVE
created_at
updated_at
```

`attributes`：

```json
{
  "color": "Black",
  "storage": "256GB"
}
```

这是 PostgreSQL `JSONB` 很好的使用场景。

---

## 6. inventory

```text
id
sku_id              FK UNIQUE
available_stock
reserved_stock
version
updated_at
```

库存模型：

```text
available_stock = 可卖库存
reserved_stock  = 已下单但尚未最终完成的库存
```

建议使用：

```text
version
```

支持乐观锁。

同时实际扣库存使用条件更新：

```sql
UPDATE inventory
SET available_stock = available_stock - :qty,
    reserved_stock = reserved_stock + :qty
WHERE sku_id = :skuId
AND available_stock >= :qty;
```

影响行数为 0：

```text
库存不足
```

这样可以防止超卖。

---

## 7. inventory_logs

```text
id
sku_id
order_id            nullable
change_type
quantity
before_stock
after_stock
operator_id         nullable
remark
created_at
```

`change_type`：

```text
PURCHASE
ORDER_RESERVED
ORDER_CANCELLED
ORDER_COMPLETED
ADMIN_ADJUSTMENT
REFUND_RETURN
```

这个表对面试非常有价值：

> 当前库存看 `inventory`，历史变更追踪看 `inventory_logs`。

---

## 8. carts

```text
id
user_id             FK UNIQUE
created_at
updated_at
```

一个用户一个购物车。

---

## 9. cart_items

```text
id
cart_id
sku_id
quantity
selected
created_at
updated_at
```

唯一约束：

```text
UNIQUE(cart_id, sku_id)
```

用户再次加入相同 SKU：

```text
quantity += ?
```

而不是新增记录。

---

# 四、订单核心设计

## 10. orders

```text
id
order_no            UNIQUE
user_id
address_id
status
subtotal_amount
discount_amount
shipping_amount
payable_amount
coupon_id            nullable
remark
cancel_reason        nullable
created_at
paid_at              nullable
shipped_at           nullable
completed_at         nullable
cancelled_at         nullable
updated_at
```

金额统一：

```text
NUMERIC(12,2)
```

Java 使用：

```text
BigDecimal
```

严禁使用 `double`。

---

## 11. order_items

订单快照表：

```text
id
order_id
product_id
sku_id
product_name
sku_attributes       JSONB
product_image
unit_price
quantity
subtotal
created_at
```

非常重要：

这里保存：

```text
product_name
sku_attributes
unit_price
product_image
```

即使之后商品：

```text
改名
改价格
删除
下架
```

历史订单仍保持原始状态。

---

## 12. order_status_logs

```text
id
order_id
from_status
to_status
operator_type       USER / ADMIN / SYSTEM
operator_id         nullable
reason              nullable
created_at
```

例如：

```text
PENDING_PAYMENT
→ PAID

operator_type = SYSTEM
```

所有订单状态变化都必须写入这里。

---

# 五、支付与履约

## 13. payments

允许一次订单产生多次支付尝试。

```text
id
payment_no          UNIQUE
order_id
method
amount
status
transaction_id      nullable
created_at
paid_at
updated_at
```

支付方式：

```text
MOCK
ALIPAY
WECHAT
```

V1 实现：

```text
MOCK
```

状态：

```text
PENDING
SUCCESS
FAILED
REFUNDED
```

---

## 14. shipments

```text
id
order_id
carrier
tracking_number
status
shipped_at
delivered_at
created_at
updated_at
```

状态：

```text
READY
SHIPPED
IN_TRANSIT
DELIVERED
```

V1 可以先模拟物流，不需要接真实物流 API。

---

## 15. refunds

```text
id
refund_no           UNIQUE
order_id
user_id
payment_id
amount
reason
status
admin_remark
created_at
approved_at
completed_at
updated_at
```

状态：

```text
REQUESTED
APPROVED
REJECTED
COMPLETED
CANCELLED
```

---

# 六、优惠券

## 16. coupons

```text
id
code                UNIQUE
name
discount_type
discount_value
minimum_amount
total_quantity
claimed_quantity
start_at
expire_at
status
created_at
```

类型：

```text
FIXED
PERCENTAGE
```

例如：

```text
满 100 减 10
```

---

## 17. user_coupons

```text
id
user_id
coupon_id
status
order_id            nullable
claimed_at
used_at              nullable
expire_at
```

状态：

```text
AVAILABLE
USED
EXPIRED
```

唯一约束建议：

```text
UNIQUE(user_id, coupon_id)
```

V1 每种优惠券每用户只能领一次。

---

# 七、审计

## 18. audit_logs

```text
id
admin_id
action
resource_type
resource_id
before_value        JSONB
after_value         JSONB
ip_address
created_at
```

例如：

```text
admin_id = 3
action = UPDATE_INVENTORY
resource_type = SKU
resource_id = 101

before:
stock = 50

after:
stock = 100
```

适用于：

```text
商品修改
库存调整
订单取消
发货
退款审批
用户禁用
```

---

# 八、推荐索引

核心索引至少：

```text
users(email)

products(category_id, status)

product_skus(product_id, status)
product_skus(sku_code)

inventory(sku_id)

orders(user_id, created_at DESC)
orders(status, created_at DESC)
orders(order_no)

order_items(order_id)

payments(order_id)
payments(payment_no)

shipments(order_id)
shipments(tracking_number)

refunds(order_id, status)

inventory_logs(sku_id, created_at DESC)

order_status_logs(order_id, created_at)

user_coupons(user_id, status)
```

---

# 九、完整订单状态机

主状态：

```text
PENDING_PAYMENT
PAID
PROCESSING
SHIPPED
DELIVERED
COMPLETED

CANCELLED

REFUND_REQUESTED
REFUNDED
```

正常流程：

```text
                 创建订单
                    │
                    ▼
           PENDING_PAYMENT
                    │
                 支付成功
                    ▼
                  PAID
                    │
              管理员确认处理
                    ▼
               PROCESSING
                    │
                  发货
                    ▼
                SHIPPED
                    │
                物流送达
                    ▼
               DELIVERED
                    │
            用户确认 / 自动确认
                    ▼
               COMPLETED
```

取消：

```text
PENDING_PAYMENT
      │
      └──────────→ CANCELLED
```

支付后退款：

```text
PAID
 │
 ├─→ REFUND_REQUESTED
 │
 └──── APPROVED
          ↓
       REFUNDED
```

也允许：

```text
PROCESSING
    ↓
REFUND_REQUESTED
```

V1 不允许：

```text
SHIPPED → 直接退款
```

发货后退款可以以后扩展成：

```text
RETURN_REQUESTED
RETURNING
RETURN_RECEIVED
REFUNDED
```

第一版不要做这么复杂。

---

# 十、订单状态流转权限

| 当前状态 | 操作 | 下一状态 | 操作者 |
|---|---|---|---|
| PENDING_PAYMENT | 支付 | PAID | USER/SYSTEM |
| PENDING_PAYMENT | 取消 | CANCELLED | USER |
| PENDING_PAYMENT | 超时 | CANCELLED | SYSTEM |
| PAID | 确认处理 | PROCESSING | ADMIN |
| PAID | 申请退款 | REFUND_REQUESTED | USER |
| PROCESSING | 发货 | SHIPPED | ADMIN |
| PROCESSING | 申请退款 | REFUND_REQUESTED | USER |
| SHIPPED | 确认送达 | DELIVERED | ADMIN/SYSTEM |
| DELIVERED | 确认收货 | COMPLETED | USER |
| DELIVERED | 超时自动完成 | COMPLETED | SYSTEM |
| REFUND_REQUESTED | 同意退款 | REFUNDED | ADMIN |
| REFUND_REQUESTED | 拒绝 | 原状态 | ADMIN |

禁止随意：

```text
PAID → COMPLETED
PENDING_PAYMENT → SHIPPED
CANCELLED → PAID
REFUNDED → PROCESSING
```

状态机必须由 Service 层统一控制。

不能让 Controller：

```java
order.setStatus(request.getStatus());
```

直接修改。

---

# 十一、创建订单事务

创建订单必须是一个事务：

```text
BEGIN

1. 验证用户
2. 查询购物车
3. 查询 SKU
4. 验证商品状态
5. 验证价格
6. 验证库存
7. 计算商品金额
8. 验证优惠券
9. 创建 orders
10. 创建 order_items
11. 扣减 available_stock
12. 增加 reserved_stock
13. 写 inventory_logs
14. 写 order_status_logs
15. 标记优惠券 USED
16. 清除购物车

COMMIT
```

任意一步失败：

```text
ROLLBACK
```

---

# 十二、幂等下单

接口：

```http
POST /api/v1/user/orders
Idempotency-Key: 59bb8e4c-...
```

Redis：

```text
order:idempotency:{userId}:{key}
```

第一次：

```text
创建订单
↓
保存 orderId
```

第二次重复请求：

```text
直接返回原 orderId
```

不创建第二个订单。

---

# 十三、User API

统一前缀：

```text
/api/v1/user
```

公共：

```http
POST   /auth/register
POST   /auth/login
POST   /auth/logout
```

用户：

```http
GET    /profile
PUT    /profile
PUT    /profile/password
```

地址：

```http
GET    /addresses
POST   /addresses
PUT    /addresses/{id}
DELETE /addresses/{id}
PUT    /addresses/{id}/default
```

分类：

```http
GET    /categories
```

商品：

```http
GET    /products
GET    /products/{id}
GET    /products/{id}/skus
```

支持：

```text
keyword
categoryId
minPrice
maxPrice
page
size
sort
```

购物车：

```http
GET    /cart
POST   /cart/items
PUT    /cart/items/{itemId}
DELETE /cart/items/{itemId}
DELETE /cart/items
PUT    /cart/items/{itemId}/select
PUT    /cart/select-all
```

订单：

```http
POST   /orders
GET    /orders
GET    /orders/{orderId}
POST   /orders/{orderId}/cancel
POST   /orders/{orderId}/confirm-receipt
POST   /orders/{orderId}/buy-again
```

查询参数：

```text
status
page
size
startDate
endDate
```

支付：

```http
POST   /orders/{orderId}/payments
GET    /orders/{orderId}/payments
```

Mock 支付：

```http
POST /payments/{paymentId}/mock-success
POST /payments/{paymentId}/mock-fail
```

物流：

```http
GET /orders/{orderId}/shipment
```

退款：

```http
POST   /orders/{orderId}/refunds
GET    /orders/{orderId}/refunds
GET    /refunds/{refundId}
POST   /refunds/{refundId}/cancel
```

优惠券：

```http
GET    /coupons/available
POST   /coupons/{couponId}/claim
GET    /my-coupons
```

通知：

```http
GET /ws/orders
```

WebSocket 推送：

```text
ORDER_PAID
ORDER_PROCESSING
ORDER_SHIPPED
ORDER_DELIVERED
ORDER_COMPLETED
ORDER_CANCELLED
REFUND_APPROVED
REFUND_REJECTED
```

---

# 十四、Admin API

统一：

```text
/api/v1/admin
```

认证：

```http
POST /auth/login
POST /auth/logout
```

用户：

```http
GET /users
GET /users/{id}
PUT /users/{id}/enable
PUT /users/{id}/disable
```

管理员：

```http
GET    /admins
POST   /admins
PUT    /admins/{id}
PUT    /admins/{id}/enable
PUT    /admins/{id}/disable
```

分类：

```http
GET    /categories
POST   /categories
PUT    /categories/{id}
DELETE /categories/{id}
PUT    /categories/{id}/enable
PUT    /categories/{id}/disable
```

商品：

```http
GET    /products
POST   /products
GET    /products/{id}
PUT    /products/{id}
DELETE /products/{id}

PUT    /products/{id}/publish
PUT    /products/{id}/unpublish
```

SKU：

```http
GET    /products/{productId}/skus
POST   /products/{productId}/skus
PUT    /skus/{skuId}
DELETE /skus/{skuId}
```

库存：

```http
GET    /inventory
GET    /inventory/{skuId}
POST   /inventory/{skuId}/adjust
GET    /inventory/{skuId}/logs
GET    /inventory/low-stock
```

订单：

```http
GET    /orders
GET    /orders/{id}

POST   /orders/{id}/process
POST   /orders/{id}/ship
POST   /orders/{id}/cancel

GET    /orders/{id}/status-history
```

订单查询：

```text
orderNo
userId
status
startDate
endDate
minAmount
maxAmount
page
size
```

物流：

```http
POST   /orders/{id}/shipments
GET    /orders/{id}/shipments
PUT    /shipments/{id}/status
```

退款：

```http
GET    /refunds
GET    /refunds/{id}
POST   /refunds/{id}/approve
POST   /refunds/{id}/reject
POST   /refunds/{id}/complete
```

优惠券：

```http
GET    /coupons
POST   /coupons
PUT    /coupons/{id}
PUT    /coupons/{id}/enable
PUT    /coupons/{id}/disable
```

Dashboard：

```http
GET /dashboard/overview
GET /dashboard/sales-trend
GET /dashboard/order-trend
GET /dashboard/order-status
GET /dashboard/top-products
GET /dashboard/low-stock
```

例如：

```json
{
  "todayRevenue": 12680.50,
  "todayOrders": 124,
  "paidOrders": 106,
  "refundOrders": 3,
  "newUsers": 18,
  "pendingShipmentOrders": 22
}
```

报表：

```http
GET /reports/sales
GET /reports/orders
GET /reports/products
GET /reports/export
```

Excel：

```text
GET /reports/export?startDate=...&endDate=...
```

文件：

```http
POST /files/images
DELETE /files/{id}
```

审计日志：

```http
GET /audit-logs
GET /audit-logs/{id}
```

WebSocket：

```text
/ws/admin/orders
```

新付款订单：

```text
用户付款成功
↓
ORDER_PAID
↓
WebSocket
↓
Admin Dashboard

New Order #ORD202611080001
```

---

# 十五、定时任务

## 未支付订单

```text
每分钟扫描

PENDING_PAYMENT
AND created_at < now() - 30 min
↓
CANCELLED
↓
释放库存
```

---

## 自动确认完成

```text
DELIVERED
AND delivered_at < now() - 7 days
↓
COMPLETED
```

---

## 优惠券过期

```text
AVAILABLE
AND expire_at < NOW()
↓
EXPIRED
```

---

# 十六、Redis 设计

商品：

```text
product:{productId}
TTL 30 min
```

商品 SKU：

```text
product:sku:{productId}
```

热门商品：

```text
product:hot
ZSET
```

验证码/登录：

```text
auth:login:{userId}
```

幂等：

```text
order:idempotency:{userId}:{key}
```

Dashboard：

```text
dashboard:overview
TTL 1 min
```

缓存策略：

```text
Read:
Redis
↓ miss
PostgreSQL
↓
Redis

Update:
PostgreSQL
↓
Delete Redis
```

即：

**Cache Aside Pattern**

---

# 十七、代码模块建议

不要全部塞在一个 package。

```text
com.orderhub

├── auth
├── user
├── address
├── category
├── product
├── inventory
├── cart
├── order
├── payment
├── shipment
├── refund
├── coupon
├── dashboard
├── report
├── audit

├── common
│   ├── exception
│   ├── result
│   ├── enums
│   └── utils

├── config
├── security
├── websocket
└── task
```

每个业务模块：

```text
controller
service
mapper
entity
dto
vo
```

---

# 十八、开发优先级

第一阶段：

```text
User
Address
Category
Product
SKU
Inventory
```

第二阶段：

```text
Cart
Order
Order Item
库存扣减
事务
幂等
订单状态机
```

第三阶段：

```text
Payment
Shipment
Refund
```

第四阶段：

```text
Redis
Scheduled Task
WebSocket
OpenAPI
```

第五阶段：

```text
Coupon
Dashboard
Excel Report
Audit Log
Docker
```

---

# 十九、OrderHub 最值得面试讲的 8 个技术点

最终至少保证自己能讲清楚：

```text
1. Product / SKU 为什么分表

2. 为什么 Order Item 要保存商品快照

3. 如何防止库存超卖

4. 为什么下单需要数据库事务

5. 如何防止重复下单

6. 订单状态机如何限制非法状态变化

7. Redis 缓存和数据库如何保持一致

8. 超时订单如何自动取消并恢复库存
```

如果这 8 个问题你都可以自己解释清楚，OrderHub 的价值就已经明显高于普通“Spring Boot CRUD 项目”。

---

## 最终核心业务链

```text
注册 / 登录
       ↓
浏览商品
       ↓
Product → SKU
       ↓
加入购物车
       ↓
提交订单
       ↓
库存检查 + 事务 + 幂等
       ↓
PENDING_PAYMENT
       ↓
模拟支付
       ↓
PAID
       ↓
后台收到 WebSocket 通知
       ↓
PROCESSING
       ↓
管理员发货
       ↓
SHIPPED
       ↓
DELIVERED
       ↓
用户确认收货
       ↓
COMPLETED
```

异常：

```text
未付款
↓
30min
↓
自动取消
↓
恢复库存


付款
↓
申请退款
↓
管理员审核
↓
REFUNDED
```