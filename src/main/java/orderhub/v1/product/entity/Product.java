package orderhub.v1.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "products", autoResultMap = true)
public class Product {
    @TableId(type = IdType.AUTO) public Long id;
    public Long categoryId;
    public String name;
    public String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) public String brand;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) public String imageUrl;
    public String status;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
