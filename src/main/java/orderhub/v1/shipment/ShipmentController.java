package orderhub.v1.shipment;

import jakarta.validation.Valid;
import java.util.List;
import orderhub.api.ApiException;
import orderhub.v1.common.Actor;
import orderhub.v1.order.*;
import orderhub.v1.shipment.entity.Shipment;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @Tag(name="V1 模拟物流")
public class ShipmentController {
    private final OrderService orders;public ShipmentController(OrderService orders){this.orders=orders;}
    @GetMapping("/api/v1/user/orders/{orderId}/shipment") public List<Shipment> shipment(@PathVariable long orderId){
        var list=orders.shipment(Actor.current(),orderId);if(list.isEmpty())throw ApiException.missing("尚未发货");return list;
    }
    @PutMapping("/api/v1/admin/shipments/{id}/status") public Shipment update(@PathVariable long id,@Valid @RequestBody OrderDtos.ShipmentStatus in){return orders.shipmentStatus(Actor.current(),id,in.status());}
}
