package orderhub.v1.category.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import orderhub.v1.common.JsonbHandler;

@TableName(value = "categories", autoResultMap = true)
public class Category {
    @TableId(type = IdType.AUTO) public Long id;
    public String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) public Long parentId;
    public Integer sortOrder;
    public String status;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
