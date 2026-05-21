package com.techeer.carpool.domain.ride.service;

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
import com.techeer.carpool.domain.ride.dto.*;
import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RidePassenger;
import com.techeer.carpool.domain.ride.entity.RideStatus;
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
@Transactional(readOnly = true)
public class RideService {

    private final RideRepository rideRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final PostRepository postRepository;
    private final DriverRepository driverRepository;
    private final ApplicationRepository applicationRepository;
    private final RedisNotificationPublisher notificationPublisher;
    private final NotificationService notificationService;
    private final CarpoolMetrics carpoolMetrics;

    @Transactional
    public RideResponse createRide(RideCreateRequest request, Long driverId) {
        driverRepository.findByMemberIdAndDeletedFalse(driverId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.DRIVER_NOT_FOUND));

        Post post = postRepository.findByIdAndDeletedFalse(request.getPostId())
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
        if (!post.getMemberId().equals(driverId)) {
            throw new CarpoolException(ErrorCode.RIDE_FORBIDDEN);
        }
        if (post.getStatus() != PostStatus.OPEN && post.getStatus() != PostStatus.CLOSED) {
            throw new CarpoolException(ErrorCode.RIDE_INVALID_STATUS);
        }
        Ride ride = rideRepository.save(Ride.builder()
                .postId(request.getPostId())
                .driverId(driverId)
                .build());

        List<Application> accepted = applicationRepository.findByPostIdAndStatus(
                request.getPostId(), ApplicationStatus.ACCEPTED);
        List<RidePassenger> passengers = accepted.stream()
                .map(app -> RidePassenger.builder()
                        .ride(ride)
                        .applicationId(app.getId())
                        .passengerId(app.getApplicantId())
                        .build())
                .collect(Collectors.toList());
        ridePassengerRepository.saveAll(passengers);

        return RideResponse.from(ride, post);
    }

    public RideResponse getRide(Long rideId) {
        Ride ride = findRideById(rideId);
        Post post = postRepository.findByIdAndDeletedFalse(ride.getPostId()).orElse(null);
        return RideResponse.from(ride, post);
    }

    @Transactional
    public RideResponse startRide(Long rideId, Long requesterId) {
        Ride ride = findRideById(rideId);
        validateDriver(ride, requesterId);
        ride.start();
        carpoolMetrics.incrementRideStarted();

        List<Long> passengerIds = ride.getPassengers().stream()
                .map(RidePassenger::getPassengerId)
                .collect(Collectors.toList());
        notificationService.saveAll(passengerIds.stream()
                .map(id -> Notification.ofRideStarted(id, rideId))
                .collect(Collectors.toList()));
        notificationPublisher.publishToMany(passengerIds, NotificationPayload.builder()
                .type(NotificationType.RIDE_STARTED)
                .message("카풀 운행이 시작되었습니다.")
                .data(Map.of("rideId", rideId))
                .build());

        Post post = postRepository.findByIdAndDeletedFalse(ride.getPostId()).orElse(null);
        return RideResponse.from(ride, post);
    }

    @Transactional
    public RideResponse completeRide(Long rideId, Long requesterId) {
        Ride ride = findRideById(rideId);
        validateDriver(ride, requesterId);
        ride.complete();
        carpoolMetrics.incrementRideCompleted();

        List<Long> passengerIds = ride.getPassengers().stream()
                .map(RidePassenger::getPassengerId)
                .collect(Collectors.toList());
        notificationService.saveAll(passengerIds.stream()
                .map(id -> Notification.ofRideEnded(id, rideId))
                .collect(Collectors.toList()));
        notificationPublisher.publishToMany(passengerIds, NotificationPayload.builder()
                .type(NotificationType.RIDE_ENDED)
                .message("카풀 운행이 종료되었습니다.")
                .data(Map.of("rideId", rideId))
                .build());

        Post post = postRepository.findByIdAndDeletedFalse(ride.getPostId()).orElse(null);
        return RideResponse.from(ride, post);
    }

    @Transactional
    public LocationResponse updateLocation(Long rideId, LocationUpdateRequest request, Long requesterId) {
        Ride ride = findRideById(rideId);
        validateDriver(ride, requesterId);
        ride.updateLocation(request.getLatitude(), request.getLongitude());
        carpoolMetrics.incrementLocationUpdated();
        return LocationResponse.from(ride);
    }

    @Transactional
    public void updateLocationDirect(Long rideId, Double latitude, Double longitude, Long requesterId) {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null || !ride.getDriverId().equals(requesterId)) return;
        if (ride.getStatus() == RideStatus.COMPLETED) return;
        ride.updateLocation(latitude, longitude);
    }

    public LocationResponse getLocation(Long rideId) {
        return LocationResponse.from(findRideById(rideId));
    }

    public List<RideResponse> getMyRidesAsDriver(Long driverId) {
        return rideRepository.findAllByDriverIdOrderByCreatedAtDesc(driverId)
                .stream()
                .map(ride -> {
                    Post post = postRepository.findByIdAndDeletedFalse(ride.getPostId()).orElse(null);
                    return RideResponse.from(ride, post);
                })
                .collect(Collectors.toList());
    }

    public List<RideResponse> getMyRidesAsPassenger(Long passengerId) {
        return ridePassengerRepository.findAllByPassengerIdOrderByCreatedAtDesc(passengerId)
                .stream()
                .map(rp -> {
                    Ride ride = rp.getRide();
                    Post post = postRepository.findByIdAndDeletedFalse(ride.getPostId()).orElse(null);
                    return RideResponse.from(ride, post);
                })
                .collect(Collectors.toList());
    }

    public List<PassengerResponse> getPassengers(Long rideId) {
        Ride ride = findRideById(rideId);
        return ride.getPassengers().stream()
                .map(PassengerResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public PassengerResponse boardPassenger(Long rideId, Long applicationId, Long requesterId) {
        Ride ride = findRideById(rideId);
        validateDriver(ride, requesterId);
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new CarpoolException(ErrorCode.RIDE_INVALID_STATUS);
        }
        RidePassenger passenger = findPassenger(rideId, applicationId);
        passenger.board();
        return PassengerResponse.from(passenger);
    }

    @Transactional
    public PassengerResponse dropOffPassenger(Long rideId, Long applicationId, Long requesterId) {
        Ride ride = findRideById(rideId);
        validateDriver(ride, requesterId);
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new CarpoolException(ErrorCode.RIDE_INVALID_STATUS);
        }
        RidePassenger passenger = findPassenger(rideId, applicationId);
        passenger.dropOff();
        return PassengerResponse.from(passenger);
    }

    public boolean isPassengerInRide(Long rideId, Long passengerId) {
        Ride ride = findRideById(rideId);
        return ride.getPassengers().stream()
                .anyMatch(p -> p.getPassengerId().equals(passengerId));
    }

    private Ride findRideById(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.RIDE_NOT_FOUND));
    }

    private RidePassenger findPassenger(Long rideId, Long applicationId) {
        return ridePassengerRepository.findByRideIdAndApplicationId(rideId, applicationId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.RIDE_PASSENGER_NOT_FOUND));
    }

    private void validateDriver(Ride ride, Long requesterId) {
        if (!ride.getDriverId().equals(requesterId)) {
            throw new CarpoolException(ErrorCode.RIDE_FORBIDDEN);
        }
    }
}
