package orderhub.v1.cart.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "carts", autoResultMap = true)
public class Cart {
    @TableId(type = IdType.AUTO) public Long id;
    public Long userId;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
