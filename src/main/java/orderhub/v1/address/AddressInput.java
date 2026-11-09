package orderhub.v1.address;

import jakarta.validation.constraints.*;

public record AddressInput(@NotBlank @Size(max=100) String receiverName,@NotBlank @Size(max=30) String phone,
    @NotBlank @Size(max=100) String province,@NotBlank @Size(max=100) String city,@NotBlank @Size(max=100) String district,
    @NotBlank @Size(max=500) String detailAddress,@Size(max=20) String postalCode,@NotNull Boolean isDefault) {}
