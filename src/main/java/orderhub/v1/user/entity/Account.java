package orderhub.v1.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "users", autoResultMap = true)
public class Account {
    @TableId(type = IdType.AUTO) public Long id;
    public String email;
    public String username;
    @JsonIgnore public String passwordHash;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) public String phone;
    public String role;
    public String status;
    @JsonIgnore public Integer tokenVersion;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
