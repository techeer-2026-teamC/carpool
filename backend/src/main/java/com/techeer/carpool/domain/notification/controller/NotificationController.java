package com.techeer.carpool.domain.notification.controller;

import com.techeer.carpool.domain.notification.dto.NotificationPage;
import com.techeer.carpool.domain.notification.emitter.SseEmitterRegistry;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
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

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication authentication) {
        return sseEmitterRegistry.subscribe((Long) authentication.getPrincipal());
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

}
