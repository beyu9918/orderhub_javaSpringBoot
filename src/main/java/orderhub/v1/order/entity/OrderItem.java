package orderhub.v1.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "order_items", autoResultMap = true)
public class OrderItem {
    @TableId(type = IdType.AUTO) public Long id;
    public Long orderId;
    public Long productId;
    public Long skuId;
    public String productName;
    @TableField(typeHandler = JsonbHandler.class) public Map<String,Object> skuAttributes;
    public String productImage;
    public BigDecimal unitPrice;
    public Integer quantity;
    public BigDecimal subtotal;
    public OffsetDateTime createdAt;
}
