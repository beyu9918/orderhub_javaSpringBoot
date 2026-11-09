package orderhub.v1.address.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.address.entity.Address;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AddressMapper extends BaseMapper<Address> {}
