package orderhub.v1.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.order.entity.StatusLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StatusLogMapper extends BaseMapper<StatusLog> {}
