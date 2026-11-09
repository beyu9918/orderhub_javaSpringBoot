package orderhub.v1.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "product_skus", autoResultMap = true)
public class Sku {
    @TableId(type = IdType.AUTO) public Long id;
    public Long productId;
    public String skuCode;
    @TableField(typeHandler = JsonbHandler.class) public Map<String,Object> attributes;
    public BigDecimal price;
    public String status;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
