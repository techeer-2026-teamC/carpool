package com.techeer.carpool.global.config;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.meeting.MeetingService;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.*;
import java.time.Instant;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingSocketSecurityTest {
    final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    final BlacklistRedisRepository blacklist = mock(BlacklistRedisRepository.class);
    final MeetingService meetings = mock(MeetingService.class);
    final WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(tokens, blacklist, meetings);

    StompHeaderAccessor frame(StompCommand command, String destination) {
        var frame = StompHeaderAccessor.create(command);
        frame.setSessionAttributes(new HashMap<>());
        frame.getSessionAttributes().put("authExpiresAt", Instant.now().plusSeconds(30));
        frame.getSessionAttributes().put("authToken", "token");
        frame.setUser(() -> "7");
        frame.setDestination(destination);
        return frame;
    }
    void send(StompHeaderAccessor frame) {
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], frame.getMessageHeaders()), null);
    }
    @Test void subscriptionAllowsOnlyOwnTopicAndChecksCurrentMembership() {
        send(frame(StompCommand.SUBSCRIBE, "/topic/meetings/42/members/7"));
        verify(meetings).get(42L, 7L);
        assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, "/topic/meetings/42/members/8")))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, "/topic/rides/42")))
                .isInstanceOf(MessagingException.class);
    }
    @Test void sendingRequiresMembershipAndUnexpiredNonRevokedAuthentication() {
        send(frame(StompCommand.SEND, "/app/meetings/42/location"));
        verify(meetings).get(42L, 7L);
        var expired = frame(StompCommand.SEND, "/app/meetings/42/location");
        expired.getSessionAttributes().put("authExpiresAt", Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> send(expired)).isInstanceOf(MessagingException.class);
        when(blacklist.isBlacklisted("token")).thenReturn(true);
        assertThatThrownBy(() -> send(frame(StompCommand.SEND, "/app/meetings/42/location")))
                .isInstanceOf(MessagingException.class);
    }
    @Test void missingConnectAuthenticationIsRejected() {
        assertThatThrownBy(() -> send(frame(StompCommand.CONNECT, ""))).isInstanceOf(MessagingException.class);
    }
    @Test void expiredSocketIsClosedAndRemovedFromExpiryScan() throws Exception {
        var sessions = new WebSocketSessions();
        var session = mock(WebSocketSession.class);
        var attributes = new HashMap<String,Object>();
        when(session.getId()).thenReturn("socket");
        when(session.getAttributes()).thenReturn(attributes);
        sessions.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);
        assertThat((Instant)attributes.get("authExpiresAt")).isAfter(Instant.now());
        attributes.put("authExpiresAt", Instant.now().minusSeconds(1));
        sessions.expire();
        sessions.expire();
        verify(session, times(1)).close(CloseStatus.POLICY_VIOLATION);
    }
}
