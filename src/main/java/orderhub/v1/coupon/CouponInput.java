package orderhub.v1.coupon;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CouponInput(@NotBlank @Size(max=64) String code,@NotBlank @Size(max=100) String name,
    @NotBlank @Pattern(regexp="FIXED|PERCENTAGE") String discountType,
    @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal discountValue,
    @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal minimumAmount,
    @NotNull @Min(1) @Max(1000000000) Integer totalQuantity,
    @NotNull OffsetDateTime startAt,@NotNull OffsetDateTime expireAt) {}
