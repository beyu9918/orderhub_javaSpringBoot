package orderhub.v1.websocket;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import orderhub.v1.auth.AuthService;
import orderhub.v1.order.OrderEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderSocket extends TextWebSocketHandler {
    private final Map<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    private final AuthService auth;private final JsonMapper json;
    public OrderSocket(AuthService auth,JsonMapper json){this.auth=auth;this.json=json;}
    @Override public void afterConnectionEstablished(WebSocketSession session){sessions.put(session.getId(),new ConcurrentWebSocketSessionDecorator(session,3000,65536));}
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){sessions.remove(session.getId());}
    @Override public void handleTransportError(WebSocketSession session,Throwable ex) throws Exception{sessions.remove(session.getId());session.close(CloseStatus.SERVER_ERROR);}
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
        if(!valid(session)){session.close(CloseStatus.POLICY_VIOLATION);return;}
        if("ping".equals(message.getPayload()))session.sendMessage(new TextMessage("pong"));
    }
    private boolean valid(WebSocketSession session){try{auth.validateToken((String)session.getAttributes().get("token"));return true;}catch(RuntimeException ex){return false;}}
    @TransactionalEventListener
    public void event(OrderEvent event){
        TextMessage message=new TextMessage(json.writeValueAsString(event));
        for(WebSocketSession session:sessions.values()){
            if(!session.isOpen()||!valid(session)){close(session);continue;}
            boolean admin=Boolean.TRUE.equals(session.getAttributes().get("adminChannel"));
            if(admin||Objects.equals(session.getAttributes().get("userId"),event.userId()))try{session.sendMessage(message);}catch(IOException|RuntimeException e){close(session);}
        }
    }
    private void close(WebSocketSession session){sessions.remove(session.getId());try{session.close(CloseStatus.POLICY_VIOLATION);}catch(IOException ignored){}}
}
