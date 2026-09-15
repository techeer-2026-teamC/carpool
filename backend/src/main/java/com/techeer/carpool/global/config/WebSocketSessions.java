package com.techeer.carpool.global.config;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessions {
    private final BlacklistRedisRepository blacklist;
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
        var revoked = new HashMap<String,Boolean>();
        boolean unavailable = false;
        // A token shared by multiple tabs needs only one lookup per scan.
        for (var session : sessions.values()) {
            String token = (String)session.getAttributes().get("authToken");
            Instant expiry = (Instant)session.getAttributes().get("authExpiresAt");
            if (token == null || revoked.containsKey(token) || (expiry != null && !Instant.now().isBefore(expiry))) continue;
            try {
                revoked.put(token,blacklist.isBlacklisted(token));
            } catch (RuntimeException failure) {
                unavailable = true;
                log.warn("Closing authenticated WebSocket sessions: token revocation lookup failed");
                break;
            }
        }
        for (var entry : sessions.entrySet()) {
            var session = entry.getValue();
            Instant expiry=(Instant)session.getAttributes().get("authExpiresAt");
            String token=(String)session.getAttributes().get("authToken");
            boolean expired = expiry != null && !Instant.now().isBefore(expiry);
            boolean denied = token != null && (unavailable || revoked.getOrDefault(token,false));
            if ((expired || denied) && sessions.remove(entry.getKey(),session)) {
                try { session.close(CloseStatus.POLICY_VIOLATION); } catch(Exception ignored) { }
            }
        }
    }
}
