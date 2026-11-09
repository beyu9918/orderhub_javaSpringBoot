package orderhub.v1.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import orderhub.v1.user.entity.Account;
import orderhub.v1.user.mapper.AccountMapper;
import orderhub.v1.cart.entity.Cart;
import orderhub.v1.cart.mapper.CartMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Order(100)
@ConditionalOnProperty(name="orderhub.v1.enabled",havingValue="true",matchIfMissing=true)
public class V1Bootstrap implements ApplicationRunner {
    private final AccountMapper users; private final CartMapper carts; private final PasswordEncoder passwords;
    private final String username,password,email;
    public V1Bootstrap(AccountMapper users,CartMapper carts,PasswordEncoder passwords,
        @Value("${orderhub.admin.username}") String username,@Value("${orderhub.admin.password}") String password,
        @Value("${orderhub.admin.email:admin@orderhub.local}") String email) {
        this.users=users;this.carts=carts;this.passwords=passwords;this.username=username;this.password=password;this.email=email;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if(username.isBlank()&&password.isBlank()) return;
        if(!username.matches("[A-Za-z0-9_]{3,50}"))throw new IllegalArgumentException("ADMIN_USERNAME must contain 3–50 letters, digits or underscores");
        Account u=users.selectOne(new QueryWrapper<Account>().eq("username",username));
        if(u!=null) { if(!"ADMIN".equals(u.role)) throw new IllegalStateException("Admin username belongs to user"); return; }
        orderhub.service.AuthService.validatePassword(password);
        u=new Account();u.username=username;u.email=email.toLowerCase(java.util.Locale.ROOT);u.passwordHash=passwords.encode(password);
        u.role="ADMIN";u.status="ACTIVE";u.tokenVersion=0;users.insert(u);
        Cart c=new Cart();c.userId=u.id;carts.insert(c);
    }
}
