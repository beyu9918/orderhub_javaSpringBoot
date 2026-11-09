package orderhub.v1.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.user.entity.Account;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {}
