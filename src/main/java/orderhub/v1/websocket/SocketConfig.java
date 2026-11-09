package orderhub.v1.websocket;

import java.util.*;
import orderhub.v1.auth.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration @EnableWebSocket
public class SocketConfig implements WebSocketConfigurer {
    private final OrderSocket socket;private final AuthService auth;private final String[] origins;
    public SocketConfig(OrderSocket socket,AuthService auth,@Value("${orderhub.v1.allowed-origins}") String[] origins){this.socket=socket;this.auth=auth;this.origins=origins;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        var handshake=new DefaultHandshakeHandler();handshake.setSupportedProtocols("orderhub");
        registry.addHandler(socket,"/ws/orders","/ws/admin/orders","/api/v1/user/ws/orders")
            .setHandshakeHandler(handshake).setAllowedOrigins(origins).addInterceptors(new HandshakeInterceptor(){
                @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
                    String token=null;String header=request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                    if(header!=null&&header.startsWith("Bearer "))token=header.substring(7);
                    if(token==null)for(String protocol:request.getHeaders().getOrEmpty("Sec-WebSocket-Protocol"))for(String part:protocol.split(","))if(part.trim().startsWith("bearer."))token=part.trim().substring(7);
                    try{
                        var jwt=auth.validateToken(Objects.requireNonNull(token));boolean admin=request.getURI().getPath().equals("/ws/admin/orders");
                        if(admin&&!"ADMIN".equals(jwt.getClaimAsString("role"))){response.setStatusCode(HttpStatus.FORBIDDEN);return false;}
                        attributes.put("token",token);attributes.put("userId",Long.valueOf(jwt.getSubject()));attributes.put("adminChannel",admin);return true;
                    }catch(RuntimeException ex){response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;}
                }
                @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception ex){}
            });
    }
}
