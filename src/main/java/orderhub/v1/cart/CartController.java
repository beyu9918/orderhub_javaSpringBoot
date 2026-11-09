package orderhub.v1.cart;

import jakarta.validation.Valid;
import orderhub.v1.cart.entity.CartItem;
import orderhub.v1.common.Actor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/user/cart") @Tag(name="V1 SKU 购物车")
public class CartController {
    private final CartService carts;public CartController(CartService carts){this.carts=carts;}
    @GetMapping public CartService.View view(){return carts.view(Actor.current().id());}
    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED) public CartItem add(@Valid @RequestBody CartDtos.Add in){return carts.add(Actor.current().id(),in.skuId(),in.quantity());}
    @PutMapping("/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void quantity(@PathVariable long itemId,@Valid @RequestBody CartDtos.Quantity in){carts.quantity(Actor.current().id(),itemId,in.quantity());}
    @DeleteMapping("/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long itemId){carts.delete(Actor.current().id(),itemId);}
    @DeleteMapping("/items") @ResponseStatus(HttpStatus.NO_CONTENT) public void clear(){carts.delete(Actor.current().id(),null);}
    @PutMapping("/items/{itemId}/select") @ResponseStatus(HttpStatus.NO_CONTENT) public void select(@PathVariable long itemId,@Valid @RequestBody CartDtos.Selection in){carts.select(Actor.current().id(),itemId,in.selected());}
    @PutMapping("/select-all") @ResponseStatus(HttpStatus.NO_CONTENT) public void selectAll(@Valid @RequestBody CartDtos.Selection in){carts.select(Actor.current().id(),null,in.selected());}
}
