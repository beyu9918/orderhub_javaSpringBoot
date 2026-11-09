package orderhub.v1.coupon.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "user_coupons", autoResultMap = true)
public class UserCoupon {
    @TableId(type = IdType.AUTO) public Long id;
    public Long userId;
    public Long couponId;
    public String status;
    public Long orderId;
    public OffsetDateTime claimedAt;
    public OffsetDateTime usedAt;
    public OffsetDateTime expireAt;
}
