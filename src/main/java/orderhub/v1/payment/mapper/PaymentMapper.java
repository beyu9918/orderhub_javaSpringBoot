package orderhub.v1.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {}
