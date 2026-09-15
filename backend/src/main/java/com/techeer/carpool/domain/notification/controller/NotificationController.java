package com.techeer.carpool.domain.notification.controller;

import com.techeer.carpool.domain.notification.dto.NotificationPage;
import com.techeer.carpool.domain.notification.emitter.SseEmitterRegistry;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.global.common.ApiResponse;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.role", havingValue = "api", matchIfMissing = true)
public class NotificationController {
    private final SseEmitterRegistry sseEmitterRegistry;
    private final NotificationService notificationService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication authentication, @RequestHeader("Authorization") String authorization) {
        long remainingMillis = jwtTokenProvider.getRemainingSeconds(authorization.substring(7)) * 1000;
        if (remainingMillis <= 0) throw new CarpoolException(ErrorCode.EXPIRED_TOKEN);
        return sseEmitterRegistry.subscribe((Long) authentication.getPrincipal(), remainingMillis);
    }

    @GetMapping
    public ApiResponse<NotificationPage> inbox(Authentication authentication,
            @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of("알림 목록", notificationService.getInbox((Long) authentication.getPrincipal(), beforeId, size));
    }

    public record UnreadCount(long count) { }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(Authentication authentication) {
        return ApiResponse.of("읽지 않은 알림 수", new UnreadCount(notificationService.getUnreadCount((Long) authentication.getPrincipal())));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(Authentication authentication, @PathVariable Long notificationId) {
        notificationService.markAsRead((Long) authentication.getPrincipal(), notificationId);
        return ApiResponse.of("알림을 읽었습니다.");
    }

    @ExceptionHandler(SseEmitterRegistry.CapacityExceededException.class)
    public ResponseEntity<ApiResponse<Void>> capacityExceeded() {
        return ResponseEntity.status(429).body(ApiResponse.of("실시간 연결이 많습니다. 알림함을 조회하며 잠시 후 다시 연결해 주세요."));
    }
}
