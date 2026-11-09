package orderhub.v1.refund;

import jakarta.validation.Valid;
import java.util.List;
import orderhub.v1.common.Actor;
import orderhub.v1.order.*;
import orderhub.v1.refund.entity.Refund;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/user") @Tag(name="V1 用户售后")
public class RefundController {
    private final OrderService orders;public RefundController(OrderService orders){this.orders=orders;}
    @PostMapping("/orders/{orderId}/refunds") @ResponseStatus(HttpStatus.CREATED) public Refund create(@PathVariable long orderId,@Valid @RequestBody OrderDtos.Reason in){return orders.requestRefund(Actor.current(),orderId,in.reason());}
    @GetMapping("/orders/{orderId}/refunds") public List<Refund> list(@PathVariable long orderId){return orders.refunds(Actor.current(),orderId);}
    @GetMapping("/refunds/{refundId}") public Refund detail(@PathVariable long refundId){return orders.refund(Actor.current(),refundId);}
    @PostMapping("/refunds/{refundId}/cancel") public Refund cancel(@PathVariable long refundId){return orders.resolveRefund(Actor.current(),refundId,"CANCEL","用户撤销申请");}
}
