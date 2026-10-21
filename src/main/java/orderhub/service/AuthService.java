package orderhub.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import orderhub.api.*;
import orderhub.api.Requests.Credentials;
import orderhub.api.Views.*;
import orderhub.domain.Entities.User;
import orderhub.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final long ttl;
    private final String dummyHash;

    public AuthService(UserMapper users, PasswordEncoder passwords, JwtEncoder encoder,
                       @Value("${orderhub.jwt.ttl-seconds}") long ttl) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.ttl = ttl;
        this.dummyHash = passwords.encode("dummy-password-for-timing");
    }

    public UserView register(Credentials input) {
        validatePassword(input.password());
        var u = new User();
        u.username = input.username();
        u.passwordHash = passwords.encode(input.password());
        u.role = "USER";
        users.insert(u);
        return new UserView(u.id, u.username, u.role);
    }

    public Token login(Credentials input) {
        validatePassword(input.password());
        User user = users.selectOne(new QueryWrapper<User>().eq("username", input.username()));
        boolean matches = passwords.matches(input.password(), user == null ? dummyHash : user.passwordHash);
        if (user == null || !matches) throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("orderhub").subject(user.id.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(ttl)).claim("role", user.role).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(token, "Bearer", ttl);
    }

    public UserView me(long id) {
        User user = users.selectById(id);
        if (user == null) throw ApiException.missing("用户不存在");
        return new UserView(user.id, user.username, user.role);
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码至少 8 个字符，UTF-8 编码不超过 72 字节");
    }
}
