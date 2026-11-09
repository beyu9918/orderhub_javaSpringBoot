package orderhub.v1.shipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.shipment.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {}
