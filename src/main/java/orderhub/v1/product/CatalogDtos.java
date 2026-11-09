package orderhub.v1.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public final class CatalogDtos {
    private CatalogDtos(){}
    public record CategoryInput(@NotBlank @Size(max=100) String name,@Positive Long parentId,@NotNull Integer sortOrder){}
    public record ProductInput(@NotNull @Positive Long categoryId,@NotBlank @Size(max=200) String name,
        @NotNull @Size(max=2000) String description,@Size(max=100) String brand,@Size(max=500) String imageUrl){}
    public record SkuInput(@NotBlank @Size(max=64) String skuCode,@NotNull @Size(max=20) Map<String,Object> attributes,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price,
        @NotBlank @Pattern(regexp="ACTIVE|INACTIVE") String status,@NotNull @Min(0) @Max(1000000) Integer initialStock){}
    public record Adjustment(@NotNull @Min(-1000000) @Max(1000000) Integer quantity,
        @NotNull @Min(0) Integer expectedVersion,@NotBlank @Size(max=500) String reason){}
}
