package orderhub.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import orderhub.api.Requests.CartInput;
import orderhub.api.Views.CartView;
import orderhub.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart/items")
@Tag(name = "购物车")
public class CartController {
    private final CartService cart;
    public CartController(CartService cart) { this.cart = cart; }

    @GetMapping @Operation(summary = "查询自己的购物车")
    public List<CartView> list(@AuthenticationPrincipal Jwt jwt) { return cart.list(user(jwt)); }

    @PutMapping("/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "添加或设置商品数量（替换数量）")
    public void put(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long productId,
                    @Valid @RequestBody CartInput input) { cart.put(user(jwt), productId, input.quantity()); }

    @DeleteMapping("/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "移除商品")
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long productId) {
        cart.remove(user(jwt), productId);
    }

    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "清空购物车")
    public void clear(@AuthenticationPrincipal Jwt jwt) { cart.clear(user(jwt)); }

    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
