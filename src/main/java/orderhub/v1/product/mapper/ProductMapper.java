package orderhub.v1.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {}
