package com.techeer.carpool.domain.notification.subscriber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.emitter.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.role", havingValue = "api", matchIfMissing = true)
public class RedisNotificationSubscriber implements MessageListener {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        onMessage(new String(message.getBody(), StandardCharsets.UTF_8),
                new String(message.getChannel(), StandardCharsets.UTF_8));
    }

    // RedisMessageListenerContainer가 "notification:{userId}" 채널 메시지 수신 시 호출
    public void onMessage(String message, String channel) {
        try {
            if (!channel.startsWith("notification:")) return;
            Long userId = Long.parseLong(channel.substring("notification:".length()));
            NotificationPayload payload = objectMapper.readValue(message, NotificationPayload.class);
            if (payload.getNotificationId() == null || payload.getCreatedAt() == null) {
                log.warn("Ignored notification without stable identity");
                return;
            }
            sseEmitterRegistry.send(userId, payload);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // 역직렬화 실패 시 해당 메시지만 스킵, 다른 구독자에게 영향 없음
            log.error("알림 메시지 역직렬화 실패: channel={}", channel, e);
        }
    }
}
