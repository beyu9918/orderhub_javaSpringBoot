package orderhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.domain.Entities.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OrderMapper extends BaseMapper<Order> {
    @Select("SELECT * FROM shop_order WHERE id = #{id} AND user_id = #{userId} FOR UPDATE")
    Order lockOwned(@Param("id") long id, @Param("userId") long userId);
}
