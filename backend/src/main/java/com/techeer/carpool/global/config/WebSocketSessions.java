package com.techeer.carpool.global.config;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessions {
    private final ConcurrentHashMap<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                session.getAttributes().put("authExpiresAt",Instant.now().plusSeconds(15));
                sessions.put(session.getId(),session);
                super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) throws Exception {
                sessions.remove(session.getId(),session);
                super.afterConnectionClosed(session,status);
            }
        };
    }
    @Scheduled(fixedDelay=5000)
    public void expire() {
        sessions.forEach((id,session)->{
            Instant expiry=(Instant)session.getAttributes().get("authExpiresAt");
            if(expiry!=null && !Instant.now().isBefore(expiry)) {
                try { session.close(CloseStatus.POLICY_VIOLATION); } catch(Exception ignored) { }
                sessions.remove(id,session);
            }
        });
    }
}
