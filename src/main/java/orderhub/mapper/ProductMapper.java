package orderhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.domain.Entities.Product;
import org.apache.ibatis.annotations.Select;

public interface ProductMapper extends BaseMapper<Product> {
    @Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    Product lockById(long id);
}
