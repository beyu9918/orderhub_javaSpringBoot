package orderhub.v1.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.inventory.entity.StockLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {}
