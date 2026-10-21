package orderhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.domain.Entities.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InventoryMapper extends BaseMapper<Inventory> {
    @Update("UPDATE inventory SET quantity = quantity - #{quantity}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE product_id = #{productId} AND quantity >= #{quantity}")
    int deduct(@Param("productId") long productId, @Param("quantity") int quantity);

    @Update("UPDATE inventory SET quantity = quantity + #{quantity}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE product_id = #{productId}")
    int restore(@Param("productId") long productId, @Param("quantity") int quantity);
}
