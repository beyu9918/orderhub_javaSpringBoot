package orderhub.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import orderhub.api.Requests.CheckoutInput;
import orderhub.api.Views.*;
import orderhub.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "从购物车下单，requestKey 保证幂等")
    public OrderView checkout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutInput input) {
        return orders.checkout(user(jwt), input.requestKey());
    }

    @GetMapping @Operation(summary = "自己的历史订单，按创建时间倒序分页")
    public PageView<OrderSummary> history(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                         @RequestParam(required = false) @Pattern(regexp = "CREATED|CANCELLED") String status) {
        return orders.history(user(jwt), page, size, status);
    }

    @GetMapping("/{id}") @Operation(summary = "自己的订单及商品快照明细")
    public OrderView detail(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return orders.detail(user(jwt), id);
    }

    @PostMapping("/{id}/cancel") @Operation(summary = "取消订单并归还库存，重复取消不重复归还")
    public OrderView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return orders.cancel(user(jwt), id);
    }

    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
