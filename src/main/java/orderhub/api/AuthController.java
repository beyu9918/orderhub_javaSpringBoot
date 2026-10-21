package orderhub.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import orderhub.api.Requests.Credentials;
import orderhub.api.Views.*;
import orderhub.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "注册普通用户") @SecurityRequirements
    public UserView register(@Valid @RequestBody Credentials input) { return auth.register(input); }

    @PostMapping("/login") @Operation(summary = "登录获取 JWT") @SecurityRequirements
    public Token login(@Valid @RequestBody Credentials input) { return auth.login(input); }

    @GetMapping("/me") @Operation(summary = "当前用户")
    public UserView me(@AuthenticationPrincipal Jwt jwt) { return auth.me(Long.parseLong(jwt.getSubject())); }
}
