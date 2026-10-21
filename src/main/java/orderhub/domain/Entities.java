package orderhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class Entities {
    private Entities() {}

    @TableName("app_user")
    public static class User {
        @TableId(type = IdType.AUTO) public Long id;
        public String username;
        public String passwordHash;
        public String role;
        public OffsetDateTime createdAt;
    }

    @TableName("product")
    public static class Product {
        @TableId(type = IdType.AUTO) public Long id;
        public String sku;
        public String name;
        public String description;
        public String category;
        public BigDecimal price;
        public Boolean active;
        public OffsetDateTime createdAt;
    }

    @TableName("inventory")
    public static class Inventory {
        @TableId(type = IdType.INPUT) public Long productId;
        public Integer quantity;
        public OffsetDateTime updatedAt;
    }

    @TableName("cart_item")
    public static class CartItem {
        @TableId(type = IdType.AUTO) public Long id;
        public Long userId;
        public Long productId;
        public Integer quantity;
    }

    @TableName("shop_order")
    public static class Order {
        @TableId(type = IdType.AUTO) public Long id;
        public String orderNo;
        public Long userId;
        public String requestKey;
        public String status;
        public BigDecimal totalAmount;
        public OffsetDateTime createdAt;
    }

    @TableName("order_item")
    public static class OrderItem {
        @TableId(type = IdType.AUTO) public Long id;
        public Long orderId;
        public Long productId;
        public String productName;
        public BigDecimal unitPrice;
        public Integer quantity;
        public BigDecimal subtotal;
    }
}
