package com.techeer.carpool.domain.ride.controller;

import com.techeer.carpool.domain.ride.dto.LocationBroadcast;
import com.techeer.carpool.domain.ride.dto.LocationMessage;
import com.techeer.carpool.domain.ride.dto.PassengerLocationBroadcast;
import com.techeer.carpool.domain.ride.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class RideWebSocketController {

    private final RideService rideService;
    private final SimpMessagingTemplate messagingTemplate;

    // 드라이버 위치 전송 → 탑승자 전원에게 브로드캐스트
    @MessageMapping("/ride/{rideId}/location")
    public void handleLocation(
            @DestinationVariable Long rideId,
            @Payload LocationMessage message,
            Principal principal) {
        if (principal == null) return;
        Long driverId = Long.parseLong(principal.getName());

        rideService.updateLocationDirect(rideId, message.getLatitude(), message.getLongitude(), driverId);

        messagingTemplate.convertAndSend("/topic/ride/" + rideId,
                LocationBroadcast.builder()
                        .rideId(rideId)
                        .latitude(message.getLatitude())
                        .longitude(message.getLongitude())
                        .timestamp(LocalDateTime.now().toString())
                        .build());
    }

    // 탑승자 위치 전송 → 드라이버에게만 브로드캐스트
    @MessageMapping("/ride/{rideId}/passenger-location")
    public void handlePassengerLocation(
            @DestinationVariable Long rideId,
            @Payload LocationMessage message,
            Principal principal) {
        if (principal == null) return;
        Long passengerId = Long.parseLong(principal.getName());

        if (!rideService.isPassengerInRide(rideId, passengerId)) return;

        messagingTemplate.convertAndSend("/topic/ride/" + rideId + "/passengers",
                PassengerLocationBroadcast.builder()
                        .rideId(rideId)
                        .passengerId(passengerId)
                        .latitude(message.getLatitude())
                        .longitude(message.getLongitude())
                        .timestamp(LocalDateTime.now().toString())
                        .build());
    }
}
