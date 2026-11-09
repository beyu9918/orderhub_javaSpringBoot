package orderhub.v1.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Instant;
import java.util.*;
import orderhub.api.ApiException;
import orderhub.api.Views.Token;
import orderhub.v1.audit.AuditService;
import orderhub.v1.cart.entity.Cart;
import orderhub.v1.cart.mapper.CartMapper;
import orderhub.v1.common.*;
import orderhub.v1.user.entity.Account;
import orderhub.v1.user.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AccountMapper users; private final CartMapper carts; private final PasswordEncoder passwords;
    private final JwtEncoder encoder; private final JwtDecoder decoder; private final AuditService audit;
    private final long ttl; private final String dummy;
    private final CommerceCache cache;
    public AuthService(AccountMapper users,CartMapper carts,PasswordEncoder passwords,JwtEncoder encoder,JwtDecoder decoder,AuditService audit,CommerceCache cache,
                       @Value("${orderhub.jwt.ttl-seconds}") long ttl) {
        this.users=users; this.carts=carts; this.passwords=passwords; this.encoder=encoder; this.decoder=decoder; this.audit=audit;
        this.ttl=ttl;this.cache=cache; dummy=passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional public Account register(AuthDtos.Register input,String role,Actor actor) {
        orderhub.service.AuthService.validatePassword(input.password());
        Account u=new Account(); u.email=input.email().toLowerCase(Locale.ROOT); u.username=input.username();
        u.phone=input.phone(); u.passwordHash=passwords.encode(input.password()); u.role=role; u.status="ACTIVE"; u.tokenVersion=0;
        users.insert(u); Cart c=new Cart(); c.userId=u.id; carts.insert(c);
        audit.record(actor,"CREATE_ADMIN","USER",u.id,null,u);
        return users.selectById(u.id);
    }
    public Token login(AuthDtos.Login input,boolean admin) {
        Account u=users.selectOne(new QueryWrapper<Account>().eq("email",input.account().toLowerCase(Locale.ROOT)).or().eq("username",input.account()));
        boolean matches=passwords.matches(input.password(),u==null?dummy:u.passwordHash);
        if(u==null||!matches||!"ACTIVE".equals(u.status)||(admin&&!"ADMIN".equals(u.role)))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"账号或密码错误，或账号已禁用");
        Instant now=Instant.now();
        cache.put("auth:login:"+u.id,JsonbHandler.encode(Map.of("tokenVersion",u.tokenVersion,"lastLogin",now.toString())),java.time.Duration.ofSeconds(ttl));
        var claims=JwtClaimsSet.builder().issuer("orderhub").subject(u.id.toString()).issuedAt(now).expiresAt(now.plusSeconds(ttl))
            .id(UUID.randomUUID().toString()).claim("role",u.role).claim("ver",u.tokenVersion).claim("api","v1").build();
        return new Token(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue(),"Bearer",ttl);
    }
    public Account validate(Jwt jwt) {
        try {
            Account u=users.selectById(Long.parseLong(jwt.getSubject()));
            Number version=jwt.getClaim("ver");
            if(u==null||!"v1".equals(jwt.getClaimAsString("api"))||!"ACTIVE".equals(u.status)||version==null
                ||version.intValue()!=u.tokenVersion||!u.role.equals(jwt.getClaimAsString("role"))) throw new IllegalArgumentException();
            return u;
        } catch(RuntimeException ex) { throw new BadJwtException("Account disabled or token revoked"); }
    }
    public Jwt validateToken(String token) { Jwt jwt=decoder.decode(token); validate(jwt); return jwt; }
    public Account me(long id) { return Db.require(users.selectById(id)); }
    @Transactional public Account profile(long id,AuthDtos.Profile input) {
        Account u=Db.lock(users,id); u.username=input.username(); u.phone=input.phone(); u.updatedAt=Db.now(); users.updateById(u); return u;
    }
    @Transactional public void password(long id,AuthDtos.Password input) {
        Account u=Db.lock(users,id); Db.check(passwords.matches(input.oldPassword(),u.passwordHash),"原密码错误");
        orderhub.service.AuthService.validatePassword(input.newPassword());
        u.passwordHash=passwords.encode(input.newPassword()); u.tokenVersion++; u.updatedAt=Db.now(); users.updateById(u);
        cache.evict("auth:login:"+id);
    }
    @Transactional public void logout(long id) {
        Account u=Db.lock(users,id); u.tokenVersion++; u.updatedAt=Db.now(); users.updateById(u);
        cache.evict("auth:login:"+id);
    }
    @Transactional public Account enable(Actor actor,long id,boolean enabled,boolean admin) {
        // Serialize account administration, including concurrent attempts to disable the last admin.
        users.selectList(new QueryWrapper<Account>().eq("role","ADMIN").orderByAsc("id").last("FOR UPDATE"));
        Account u=Db.lock(users,id); Db.check(admin=="ADMIN".equals(u.role),"账号类型不匹配");
        if(!enabled&&admin) {
            Db.check(!u.id.equals(actor.id()),"不能禁用当前管理员");
            Db.check(!"ACTIVE".equals(u.status)||users.selectCount(new QueryWrapper<Account>().eq("role","ADMIN").eq("status","ACTIVE"))>1,"至少保留一个启用的管理员");
        }
        var before=JsonbHandler.object(u); u.status=enabled?"ACTIVE":"DISABLED"; u.tokenVersion++; u.updatedAt=Db.now(); users.updateById(u);
        audit.record(actor,enabled?"ENABLE_USER":"DISABLE_USER","USER",u.id,before,u); return u;
    }
    @Transactional public Account editAdmin(Actor actor,long id,AuthDtos.Profile input) {
        Account u=Db.lock(users,id); Db.check("ADMIN".equals(u.role),"不是管理员"); var before=JsonbHandler.object(u);
        u.username=input.username(); u.phone=input.phone(); u.updatedAt=Db.now(); users.updateById(u);
        audit.record(actor,"UPDATE_ADMIN","USER",id,before,u); return u;
    }
}
