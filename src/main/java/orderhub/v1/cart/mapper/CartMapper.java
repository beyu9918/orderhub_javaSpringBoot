package orderhub.v1.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.cart.entity.Cart;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {}
