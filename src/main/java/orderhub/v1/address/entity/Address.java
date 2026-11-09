package orderhub.v1.address.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "addresses", autoResultMap = true)
public class Address {
    @TableId(type = IdType.AUTO) public Long id;
    public Long userId;
    public String receiverName;
    public String phone;
    public String province;
    public String city;
    public String district;
    public String detailAddress;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) public String postalCode;
    public Boolean isDefault;
    @JsonIgnore @TableField(updateStrategy = FieldStrategy.ALWAYS) public Integer defaultSlot;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
