package orderhub.v1.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "orders", autoResultMap = true)
public class PurchaseOrder {
    @TableId(type = IdType.AUTO) public Long id;
    public String orderNo;
    public Long userId;
    public Long addressId;
    @TableField(typeHandler = JsonbHandler.class) public Map<String,Object> addressSnapshot;
    @JsonIgnore public String requestKey;
    @JsonIgnore public String requestHash;
    public String status;
    public BigDecimal subtotalAmount;
    public BigDecimal discountAmount;
    public BigDecimal shippingAmount;
    public BigDecimal payableAmount;
    public Long couponId;
    public String remark;
    public String cancelReason;
    public OffsetDateTime createdAt;
    public OffsetDateTime paidAt;
    public OffsetDateTime shippedAt;
    public OffsetDateTime deliveredAt;
    public OffsetDateTime completedAt;
    public OffsetDateTime cancelledAt;
    public OffsetDateTime updatedAt;
}
