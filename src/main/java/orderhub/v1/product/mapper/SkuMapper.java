package orderhub.v1.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.product.entity.Sku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {}
