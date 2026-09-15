package com.techeer.carpool.domain.notification.controller;

import com.techeer.carpool.domain.notification.emitter.SseEmitterRegistry;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationSubscriptionTest {
    @Test
    void subscriptionUsesAuthenticatedUserAndRemainingTokenLifetime() {
        var registry = mock(SseEmitterRegistry.class);
        var tokens = mock(JwtTokenProvider.class);
        var controller = new NotificationController(registry, mock(NotificationService.class), tokens);
        var authentication = new UsernamePasswordAuthenticationToken(99L, null);
        when(tokens.getRemainingSeconds("access-token")).thenReturn(12L);
        controller.subscribe(authentication, "Bearer access-token");
        verify(registry).subscribe(99L, 12_000L);

        when(tokens.getRemainingSeconds("access-token")).thenReturn(0L);
        assertThatThrownBy(() -> controller.subscribe(authentication, "Bearer access-token"))
                .isInstanceOf(CarpoolException.class);
        verifyNoMoreInteractions(registry);
        assertThat(controller.capacityExceeded().getStatusCode().value()).isEqualTo(429);
    }
}
