package orderhub.v1.config;

import orderhub.v1.auth.AuthService;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import java.util.List;

@Configuration
public class V1SecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain commerceSecurity(HttpSecurity http,AuthService auth) throws Exception {
        return http.securityMatcher("/api/v1/**","/ws/**")
            .csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/v1/user/auth/register","/api/v1/user/auth/login","/api/v1/admin/auth/login","/ws/**","/api/v1/user/ws/orders").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/user/products/**","/api/v1/user/products","/api/v1/user/categories","/api/v1/images/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwt->{
                try {
                    var user=auth.validate(jwt);
                    return new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+user.role)));
                } catch(JwtException ex) { throw new InvalidBearerTokenException(ex.getMessage(),ex); }
            }))).build();
    }
}
