package orderhub.v1.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.inventory.entity.Stock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockMapper extends BaseMapper<Stock> {}
