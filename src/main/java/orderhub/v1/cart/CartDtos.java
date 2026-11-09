package orderhub.v1.cart;

import jakarta.validation.constraints.*;

public final class CartDtos {
    private CartDtos(){}
    public record Add(@NotNull @Positive Long skuId,@NotNull @Min(1) @Max(999) Integer quantity){}
    public record Quantity(@NotNull @Min(1) @Max(999) Integer quantity){}
    public record Selection(@NotNull Boolean selected){}
}
