package orderhub.v1.auth;

import jakarta.validation.Valid;
import orderhub.api.Views.Token;
import orderhub.v1.common.Actor;
import orderhub.v1.user.entity.Account;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController @RequestMapping("/api/v1/user") @Tag(name="V1 用户认证与资料")
public class AuthController {
    private final AuthService auth;public AuthController(AuthService auth){this.auth=auth;}
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED) @SecurityRequirements
    public Account register(@Valid @RequestBody AuthDtos.Register in){return auth.register(in,"USER",Actor.system());}
    @PostMapping("/auth/login") @SecurityRequirements public Token login(@Valid @RequestBody AuthDtos.Login in){return auth.login(in,false);}
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(){auth.logout(Actor.current().id());}
    @GetMapping("/profile") public Account profile(){return auth.me(Actor.current().id());}
    @PutMapping("/profile") public Account profile(@Valid @RequestBody AuthDtos.Profile in){return auth.profile(Actor.current().id(),in);}
    @PutMapping("/profile/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void password(@Valid @RequestBody AuthDtos.Password in){auth.password(Actor.current().id(),in);}
}
