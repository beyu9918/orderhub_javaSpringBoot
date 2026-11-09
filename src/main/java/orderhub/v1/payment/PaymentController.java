package orderhub.v1.payment;

import jakarta.validation.Valid;
import java.util.List;
import orderhub.v1.common.Actor;
import orderhub.v1.order.*;
import orderhub.v1.payment.entity.Payment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/user") @Tag(name="V1 模拟支付")
public class PaymentController {
    private final OrderService orders;public PaymentController(OrderService orders){this.orders=orders;}
    @PostMapping("/orders/{orderId}/payments") @ResponseStatus(HttpStatus.CREATED) public Payment create(@PathVariable long orderId,@Valid @RequestBody OrderDtos.PaymentInput in){return orders.createPayment(Actor.current(),orderId,in.method());}
    @GetMapping("/orders/{orderId}/payments") public List<Payment> list(@PathVariable long orderId){return orders.payments(Actor.current(),orderId);}
    @PostMapping("/payments/{paymentId}/mock-success") public Payment success(@PathVariable long paymentId){return orders.paymentResult(Actor.current(),paymentId,true);}
    @PostMapping("/payments/{paymentId}/mock-fail") public Payment fail(@PathVariable long paymentId){return orders.paymentResult(Actor.current(),paymentId,false);}
}
