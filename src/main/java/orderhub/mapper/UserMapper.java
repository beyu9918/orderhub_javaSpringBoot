package orderhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.domain.Entities.User;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM app_user WHERE id = #{id} FOR UPDATE")
    User lockById(long id);
}
