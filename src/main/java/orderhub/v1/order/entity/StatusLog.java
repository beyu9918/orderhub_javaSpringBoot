package orderhub.v1.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "order_status_logs", autoResultMap = true)
public class StatusLog {
    @TableId(type = IdType.AUTO) public Long id;
    public Long orderId;
    public String fromStatus;
    public String toStatus;
    public String operatorType;
    public Long operatorId;
    public String reason;
    public OffsetDateTime createdAt;
}
