package orderhub.v1.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "inventory", autoResultMap = true)
public class Stock {
    @TableId(type = IdType.AUTO) public Long id;
    public Long skuId;
    public Integer availableStock;
    public Integer reservedStock;
    public Integer version;
    public OffsetDateTime updatedAt;
}
