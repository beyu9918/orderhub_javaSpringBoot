package orderhub.v1.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class OrderDtos {
    private OrderDtos(){}
    public record Checkout(@NotNull @Positive Long addressId,@Positive Long userCouponId,@Size(max=500) String remark){}
    public record Reason(@NotBlank @Size(max=500) String reason){}
    public record Ship(@NotBlank @Size(max=100) String carrier,@NotBlank @Size(max=100) String trackingNumber){}
    public record ShipmentStatus(@NotBlank @Pattern(regexp="IN_TRANSIT|DELIVERED") String status){}
    public record PaymentInput(@NotBlank @Pattern(regexp="MOCK") String method){}
    public record Query(String orderNo,Long userId,String status,OffsetDateTime startDate,OffsetDateTime endDate,
        BigDecimal minAmount,BigDecimal maxAmount,int page,int size){}
}
