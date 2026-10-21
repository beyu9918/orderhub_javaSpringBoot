package orderhub.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class Requests {
    private Requests() {}

    public record Credentials(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}") String username,
            @NotBlank @Size(min = 8, max = 72) @Schema(format = "password") String password) {}

    public record ProductInput(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Size(max = 2000) String description,
            @NotBlank @Size(max = 100) String category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull Boolean active,
            @NotNull @Min(0) @Max(1000000) Integer stock) {}

    public record StockInput(@NotNull @Min(0) @Max(1000000) Integer quantity) {}
    public record CartInput(@NotNull @Min(1) @Max(999) Integer quantity) {}
    public record CheckoutInput(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{8,64}")
                                @Schema(description = "客户端生成的幂等键，同一用户重试时保持不变") String requestKey) {}
}
