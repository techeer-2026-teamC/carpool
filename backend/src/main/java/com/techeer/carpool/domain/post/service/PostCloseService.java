package com.techeer.carpool.domain.post.service;

import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.notification.type.NotificationType;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostStatus;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RidePassenger;
import com.techeer.carpool.domain.ride.repository.RidePassengerRepository;
import com.techeer.carpool.domain.ride.repository.RideRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostCloseService {

    private final PostRepository postRepository;
    private final RideRepository rideRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final ApplicationRepository applicationRepository;
    private final DriverRepository driverRepository;
    private final NotificationService notificationService;
    private final RedisNotificationPublisher notificationPublisher;
    private final CarpoolMetrics carpoolMetrics;

    @Transactional
    public void closePost(Long postId, Long requesterId) {
        driverRepository.findByMemberIdAndDeletedFalse(requesterId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.DRIVER_NOT_FOUND));

        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMemberId().equals(requesterId)) {
            throw new CarpoolException(ErrorCode.POST_FORBIDDEN);
        }
        if (post.getStatus() == PostStatus.CLOSED) {
            throw new CarpoolException(ErrorCode.POST_ALREADY_CLOSED);
        }

        post.close();
        carpoolMetrics.incrementPostClosed();

        Ride ride = rideRepository.save(Ride.builder()
                .postId(postId)
                .driverId(requesterId)
                .build());

        List<Application> accepted = applicationRepository.findByPostIdAndStatus(postId, ApplicationStatus.ACCEPTED);
        List<RidePassenger> passengers = accepted.stream()
                .map(app -> RidePassenger.builder()
                        .ride(ride)
                        .applicationId(app.getId())
                        .passengerId(app.getApplicantId())
                        .build())
                .collect(Collectors.toList());
        ridePassengerRepository.saveAll(passengers);

        List<Long> passengerIds = passengers.stream()
                .map(RidePassenger::getPassengerId)
                .collect(Collectors.toList());
        notificationService.saveAll(passengerIds.stream()
                .map(pid -> Notification.ofRideStarted(pid, ride.getId()))
                .collect(Collectors.toList()));
        notificationPublisher.publishToMany(passengerIds, NotificationPayload.builder()
                .type(NotificationType.RIDE_STARTED)
                .message("카풀이 시작되었습니다.")
                .data(Map.of("rideId", ride.getId()))
                .build());
    }
}
