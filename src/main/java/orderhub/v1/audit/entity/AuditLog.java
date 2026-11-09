package orderhub.v1.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "audit_logs", autoResultMap = true)
public class AuditLog {
    @TableId(type = IdType.AUTO) public Long id;
    public Long adminId;
    public String action;
    public String resourceType;
    public Long resourceId;
    @TableField(typeHandler = JsonbHandler.class) public Map<String,Object> beforeValue;
    @TableField(typeHandler = JsonbHandler.class) public Map<String,Object> afterValue;
    public String ipAddress;
    public OffsetDateTime createdAt;
}
