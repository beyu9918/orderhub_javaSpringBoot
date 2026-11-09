package orderhub.v1.common;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record Actor(Long id,String role) {
    public boolean admin() { return "ADMIN".equals(role); }
    public String type() { return id == null ? "SYSTEM" : admin() ? "ADMIN" : "USER"; }
    public static Actor system() { return new Actor(null,"SYSTEM"); }
    public static Actor current() {
        var auth=(JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication();
        return new Actor(Long.valueOf(auth.getToken().getSubject()),auth.getToken().getClaimAsString("role"));
    }
}
