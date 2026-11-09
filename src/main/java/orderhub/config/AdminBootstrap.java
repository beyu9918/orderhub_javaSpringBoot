package orderhub.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import orderhub.domain.Entities.User;
import orderhub.mapper.UserMapper;
import orderhub.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="orderhub.legacy-api.enabled",havingValue="true")
public class AdminBootstrap implements ApplicationRunner {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;

    public AdminBootstrap(UserMapper users, PasswordEncoder passwords,
                          @Value("${orderhub.admin.username}") String username,
                          @Value("${orderhub.admin.password}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) return;
        if (!username.matches("[a-zA-Z0-9_]{3,50}")) throw new IllegalArgumentException("Invalid ADMIN_USERNAME");
        AuthService.validatePassword(password);
        User existing = users.selectOne(new QueryWrapper<User>().eq("username", username));
        if (existing != null) {
            if (!"ADMIN".equals(existing.role)) throw new IllegalStateException("ADMIN_USERNAME belongs to a regular user");
            return;
        }
        User admin = new User();
        admin.username = username;
        admin.passwordHash = passwords.encode(password);
        admin.role = "ADMIN";
        users.insert(admin);
    }
}
