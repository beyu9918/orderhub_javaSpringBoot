package orderhub.v1.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "inventory_logs", autoResultMap = true)
public class StockLog {
    @TableId(type = IdType.AUTO) public Long id;
    public Long skuId;
    public Long orderId;
    public String changeType;
    public Integer quantity;
    public Integer beforeStock;
    public Integer afterStock;
    public Integer beforeReserved;
    public Integer afterReserved;
    public Long operatorId;
    public String remark;
    public OffsetDateTime createdAt;
}
