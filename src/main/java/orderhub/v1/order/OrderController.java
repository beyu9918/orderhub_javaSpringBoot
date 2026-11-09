package orderhub.v1.order;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import orderhub.v1.cart.CartService;
import orderhub.v1.common.*;
import orderhub.v1.order.entity.PurchaseOrder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/user/orders") @Tag(name="V1 用户订单")
public class OrderController {
    private final OrderService orders;public OrderController(OrderService orders){this.orders=orders;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public OrderService.Detail checkout(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody OrderDtos.Checkout in){return orders.checkout(Actor.current(),key,in);}
    @GetMapping public PageResult<PurchaseOrder> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate){
        return orders.list(Actor.current(),new OrderDtos.Query(null,null,status,startDate,endDate,null,null,page,size));
    }
    @GetMapping("/{orderId}") public OrderService.Detail detail(@PathVariable long orderId){return orders.detail(Actor.current(),orderId);}
    @PostMapping("/{orderId}/cancel") public OrderService.Detail cancel(@PathVariable long orderId,@Valid @RequestBody OrderDtos.Reason in){return orders.cancel(Actor.current(),orderId,in.reason());}
    @PostMapping("/{orderId}/confirm-receipt") public OrderService.Detail complete(@PathVariable long orderId){return orders.complete(Actor.current(),orderId);}
    @PostMapping("/{orderId}/buy-again") public CartService.View buyAgain(@PathVariable long orderId){return orders.buyAgain(Actor.current(),orderId);}
}
