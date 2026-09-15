package com.techeer.carpool.global.config;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.meeting.MeetingService;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final JwtTokenProvider tokens;
    private final BlacklistRedisRepository blacklist;
    private final MeetingService meetings;
    private static final Pattern SUB = Pattern.compile("/topic/meetings/(\\d+)/members/(\\d+)");
    private static final Pattern SEND = Pattern.compile("/app/meetings/(\\d+)/location");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor a = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (a == null || a.getCommand() == null) return message;
        if (a.getCommand()==StompCommand.CONNECT) {
            String header=a.getFirstNativeHeader("Authorization");
            if(header==null || !header.startsWith("Bearer ")) throw new MessagingException("Authentication required");
            String token=header.substring(7);
            if(!tokens.validateToken(token) || blacklist.isBlacklisted(token)) throw new MessagingException("Invalid authentication");
            Long id=tokens.getMemberIdFromToken(token);
            a.setUser(()->id.toString());
            a.getSessionAttributes().put("authExpiresAt", Instant.now().plusSeconds(tokens.getRemainingSeconds(token)));
            a.getSessionAttributes().put("authToken",token);
        } else if(a.getCommand()==StompCommand.SUBSCRIBE || a.getCommand()==StompCommand.SEND) {
            Instant expiry=(Instant)a.getSessionAttributes().get("authExpiresAt");
            if(a.getUser()==null || expiry==null || !Instant.now().isBefore(expiry)
                    || blacklist.isBlacklisted((String)a.getSessionAttributes().get("authToken")))
                throw new MessagingException("Authentication expired");
            Long requester=Long.valueOf(a.getUser().getName());
            String destination=a.getDestination();
            var matcher=(a.getCommand()==StompCommand.SUBSCRIBE?SUB:SEND).matcher(destination==null?"":destination);
            if(!matcher.matches()) throw new MessagingException("Destination not allowed");
            if(a.getCommand()==StompCommand.SUBSCRIBE && !requester.equals(Long.valueOf(matcher.group(2))))
                throw new MessagingException("Cannot subscribe to another participant");
            meetings.get(Long.valueOf(matcher.group(1)),requester);
        }
        return message;
    }
}
