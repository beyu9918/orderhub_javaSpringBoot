package orderhub.v1.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {}
