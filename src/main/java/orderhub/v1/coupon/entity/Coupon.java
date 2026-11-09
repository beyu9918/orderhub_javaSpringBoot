package orderhub.v1.coupon.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "coupons", autoResultMap = true)
public class Coupon {
    @TableId(type = IdType.AUTO) public Long id;
    public String code;
    public String name;
    public String discountType;
    public BigDecimal discountValue;
    public BigDecimal minimumAmount;
    public Integer totalQuantity;
    public Integer claimedQuantity;
    public OffsetDateTime startAt;
    public OffsetDateTime expireAt;
    public String status;
    public OffsetDateTime createdAt;
}
