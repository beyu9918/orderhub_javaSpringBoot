package orderhub.v1.shipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "shipments", autoResultMap = true)
public class Shipment {
    @TableId(type = IdType.AUTO) public Long id;
    public Long orderId;
    public String carrier;
    public String trackingNumber;
    public String status;
    public OffsetDateTime shippedAt;
    public OffsetDateTime deliveredAt;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
