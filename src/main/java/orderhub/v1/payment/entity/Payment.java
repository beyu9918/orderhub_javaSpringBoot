package orderhub.v1.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "payments", autoResultMap = true)
public class Payment {
    @TableId(type = IdType.AUTO) public Long id;
    public String paymentNo;
    public Long orderId;
    public String method;
    public BigDecimal amount;
    public String status;
    public String transactionId;
    public OffsetDateTime createdAt;
    public OffsetDateTime paidAt;
    public OffsetDateTime updatedAt;
}
