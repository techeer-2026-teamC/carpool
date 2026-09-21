package com.techeer.carpool.domain.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(name="app.role",havingValue="api",matchIfMissing=true)
public class MeetingSocket {
    private final MeetingLocations locations;
    public record Update(double latitude, double longitude, String generation) {}
    @MessageMapping("/meetings/{postId}/location")
    public void update(@DestinationVariable Long postId, @Payload Update position, Principal principal) {
        locations.update(postId, Long.valueOf(principal.getName()), position.latitude(), position.longitude(), position.generation());
    }

    @Configuration
    @RequiredArgsConstructor
    @Slf4j
    @ConditionalOnProperty(name="app.role",havingValue="api",matchIfMissing=true)
    public static class Fanout implements org.springframework.data.redis.connection.MessageListener {
        private final ObjectMapper json;
        private final MeetingService meetings;
        private final SimpMessagingTemplate messages;
        private final MeetingLocations locations;
        @Override
        public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
            try {
                MeetingLocations.Position p = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), MeetingLocations.Position.class);
                MeetingView view = meetings.get(p.postId(), p.memberId());
                if (!view.locationSharingAvailable() || !locations.isCurrent(p)) return;
                for (MeetingView.Participant recipient : view.participants()) {
                    if (p.memberId().equals(view.hostId()) || recipient.host() || recipient.memberId().equals(p.memberId()))
                        messages.convertAndSend("/topic/meetings/"+p.postId()+"/members/"+recipient.memberId(), p);
                }
            } catch (Exception e) { log.debug("Meeting position no longer accessible or invalid"); }
        }
    }
}
