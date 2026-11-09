package orderhub.v1.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {}
