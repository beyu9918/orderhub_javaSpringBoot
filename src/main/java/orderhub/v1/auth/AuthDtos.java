package orderhub.v1.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {}
    public record Register(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,50}") String username,
        @NotBlank @Size(min=8,max=72) String password, @Size(max=30) String phone) {}
    public record Login(@NotBlank @Size(max=254) String account,@NotBlank @Size(max=72) String password) {}
    public record Profile(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,50}") String username,@Size(max=30) String phone) {}
    public record Password(@NotBlank String oldPassword,@NotBlank @Size(min=8,max=72) String newPassword) {}
}
