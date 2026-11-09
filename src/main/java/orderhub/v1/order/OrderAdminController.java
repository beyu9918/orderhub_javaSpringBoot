package orderhub.v1.order;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import orderhub.v1.common.*;
import orderhub.v1.order.entity.*;
import orderhub.v1.shipment.entity.Shipment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/admin/orders") @Tag(name="V1 订单履约管理")
public class OrderAdminController {
    private final OrderService orders;public OrderAdminController(OrderService orders){this.orders=orders;}
    @GetMapping public PageResult<PurchaseOrder> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String orderNo,@RequestParam(required=false) Long userId,@RequestParam(required=false) String status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
        @RequestParam(required=false) BigDecimal minAmount,@RequestParam(required=false) BigDecimal maxAmount){
        return orders.list(Actor.current(),new OrderDtos.Query(orderNo,userId,status,startDate,endDate,minAmount,maxAmount,page,size));
    }
    @GetMapping("/{id}") public OrderService.Detail detail(@PathVariable long id){return orders.detail(Actor.current(),id);}
    @PostMapping("/{id}/process") public OrderService.Detail process(@PathVariable long id){return orders.process(Actor.current(),id);}
    @PostMapping("/{id}/ship") public Shipment ship(@PathVariable long id,@Valid @RequestBody OrderDtos.Ship in){return orders.ship(Actor.current(),id,in);}
    @PostMapping("/{id}/cancel") public OrderService.Detail cancel(@PathVariable long id,@Valid @RequestBody OrderDtos.Reason in){return orders.cancel(Actor.current(),id,in.reason());}
    @GetMapping("/{id}/status-history") public List<StatusLog> history(@PathVariable long id){return orders.history(Actor.current(),id);}
    @PostMapping("/{id}/shipments") public Shipment shipment(@PathVariable long id,@Valid @RequestBody OrderDtos.Ship in){return orders.ship(Actor.current(),id,in);}
    @GetMapping("/{id}/shipments") public List<Shipment> shipments(@PathVariable long id){return orders.shipment(Actor.current(),id);}
}
