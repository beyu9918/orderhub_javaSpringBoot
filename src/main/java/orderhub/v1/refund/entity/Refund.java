package orderhub.v1.refund.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "refunds", autoResultMap = true)
public class Refund {
    @TableId(type = IdType.AUTO) public Long id;
    public String refundNo;
    public Long orderId;
    public Long userId;
    public Long paymentId;
    public BigDecimal amount;
    public String reason;
    public String status;
    public String previousOrderStatus;
    public String adminRemark;
    public OffsetDateTime createdAt;
    public OffsetDateTime approvedAt;
    public OffsetDateTime completedAt;
    public OffsetDateTime updatedAt;
}
