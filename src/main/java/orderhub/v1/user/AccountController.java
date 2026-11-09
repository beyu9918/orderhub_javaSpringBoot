package orderhub.v1.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import orderhub.api.Views.Token;
import orderhub.v1.auth.*;
import orderhub.v1.common.*;
import orderhub.v1.user.entity.Account;
import orderhub.v1.user.mapper.AccountMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController @RequestMapping("/api/v1/admin") @Tag(name="V1 管理员认证与账号")
public class AccountController {
    private final AuthService auth;private final AccountMapper users;
    public AccountController(AuthService auth,AccountMapper users){this.auth=auth;this.users=users;}
    @PostMapping("/auth/login") @SecurityRequirements public Token login(@Valid @RequestBody AuthDtos.Login in){return auth.login(in,true);}
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(){auth.logout(Actor.current().id());}
    @GetMapping("/users") public PageResult<Account> users(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String keyword){return accounts("USER",page,size,keyword);}
    @GetMapping("/users/{id}") public Account user(@PathVariable long id){Account a=auth.me(id);Db.check("USER".equals(a.role),"不是普通用户");return a;}
    @PutMapping("/users/{id}/enable") public Account enable(@PathVariable long id){return auth.enable(Actor.current(),id,true,false);}
    @PutMapping("/users/{id}/disable") public Account disable(@PathVariable long id){return auth.enable(Actor.current(),id,false,false);}
    @GetMapping("/admins") public PageResult<Account> admins(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String keyword){return accounts("ADMIN",page,size,keyword);}
    @PostMapping("/admins") @ResponseStatus(HttpStatus.CREATED) public Account admin(@Valid @RequestBody AuthDtos.Register in){return auth.register(in,"ADMIN",Actor.current());}
    @PutMapping("/admins/{id}") public Account edit(@PathVariable long id,@Valid @RequestBody AuthDtos.Profile in){return auth.editAdmin(Actor.current(),id,in);}
    @PutMapping("/admins/{id}/enable") public Account enableAdmin(@PathVariable long id){return auth.enable(Actor.current(),id,true,true);}
    @PutMapping("/admins/{id}/disable") public Account disableAdmin(@PathVariable long id){return auth.enable(Actor.current(),id,false,true);}
    private PageResult<Account> accounts(String role,int page,int size,String keyword){
        var q=new QueryWrapper<Account>().eq("role",role);if(keyword!=null&&!keyword.isBlank())q.and(w->w.like("username",keyword).or().like("email",keyword));
        return PageResult.of(users,page,size,q.orderByDesc("id"));
    }
}
