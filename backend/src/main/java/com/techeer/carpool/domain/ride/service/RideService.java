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
import com.techeer.carpool.domain.ride.entity.RideLocation;
import com.techeer.carpool.domain.ride.repository.RideLocationRedisRepository;
import com.techeer.carpool.domain.ride.repository.RideLocationRepository;
import com.techeer.carpool.domain.ride.repository.RidePassengerRepository;
import com.techeer.carpool.domain.ride.repository.RideRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RideService {

    private final RideRepository rideRepository;
    private final RideLocationRepository rideLocationRepository;
    private final RideLocationRedisRepository rideLocationRedisRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final PostRepository postRepository;
    private final DriverRepository driverRepository;
    private final ApplicationRepository applicationRepository;
    private final RedisNotificationPublisher notificationPublisher;
    private final NotificationService notificationService;
    private final CarpoolMetrics carpoolMetrics;
    private final com.techeer.carpool.domain.member.repository.MemberRepository memberRepository;

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
        // 위치 hot path 검증용 driverId 캐시 적재 (메시지마다 DB 조회 제거)
        rideLocationRedisRepository.cacheDriver(rideId, ride.getDriverId());

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

        // Redis List → DB 배치 INSERT (Write-Behind flush)
        List<RideLocationEntry> entries = rideLocationRedisRepository.getAll(rideId);
        if (!entries.isEmpty()) {
            List<RideLocation> locations = entries.stream()
                    .map(e -> RideLocation.of(rideId, e.getLatitude(), e.getLongitude(), e.getRecordedAt()))
                    .collect(Collectors.toList());
            rideLocationRepository.saveAll(locations);
        }
        // DB 커밋 성공 후 Redis 키 삭제 (커밋 전 삭제 시 데이터 유실 방지)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rideLocationRedisRepository.delete(rideId);
                rideLocationRedisRepository.evictDriver(rideId);
            }
        });

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

    // [1단계] 위치마다 DB에 직접 INSERT + 인가도 DB SELECT (DB 2회/메시지) — 최적화 전 베이스라인
    @Transactional
    public void updateLocationDirect(Long rideId, Double latitude, Double longitude, Long requesterId) {
        Ride ride = rideRepository.findById(rideId).orElse(null);   // 인가: DB SELECT
        if (ride == null || !ride.getDriverId().equals(requesterId)) return;
        if (ride.getStatus() == RideStatus.COMPLETED) return;
        rideLocationRepository.save(RideLocation.of(rideId, latitude, longitude, java.time.LocalDateTime.now()));  // 위치: DB INSERT
        carpoolMetrics.incrementLocationUpdated();
    }

    public LocationResponse getLocation(Long rideId) {
        // 운행 중: Redis에서 최신 위치 조회, 없으면 DB fallback
        RideLocationEntry entry = rideLocationRedisRepository.getLatest(rideId);
        if (entry != null) {
            return LocationResponse.fromEntry(rideId, entry);
        }
        RideLocation latest = rideLocationRepository.findTopByRideIdOrderByRecordedAtDesc(rideId).orElse(null);
        return LocationResponse.from(rideId, latest);
    }

    public List<RideResponse> getMyRidesAsDriver(Long driverId) {
        List<Ride> rides = rideRepository.findAllByDriverIdOrderByCreatedAtDesc(driverId);
        Set<Long> postIds = rides.stream().map(Ride::getPostId).collect(Collectors.toSet());
        Map<Long, Post> postMap = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        return rides.stream()
                .map(ride -> RideResponse.from(ride, postMap.get(ride.getPostId())))
                .collect(Collectors.toList());
    }

    public List<RideResponse> getMyRidesAsPassenger(Long passengerId) {
        List<RidePassenger> ridePassengers =
                ridePassengerRepository.findAllByPassengerIdWithRideOrderByCreatedAtDesc(passengerId);
        Set<Long> postIds = ridePassengers.stream()
                .map(rp -> rp.getRide().getPostId())
                .collect(Collectors.toSet());
        Map<Long, Post> postMap = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        return ridePassengers.stream()
                .map(rp -> RideResponse.from(rp.getRide(), postMap.get(rp.getRide().getPostId())))
                .collect(Collectors.toList());
    }

    public List<PassengerResponse> getPassengers(Long rideId) {
        Ride ride = findRideById(rideId);
        List<Long> ids = ride.getPassengers().stream()
                .map(RidePassenger::getPassengerId).collect(Collectors.toList());
        Map<Long, String> nicknameMap = memberRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        com.techeer.carpool.domain.member.entity.Member::getId,
                        com.techeer.carpool.domain.member.entity.Member::getNickname));
        return ride.getPassengers().stream()
                .map(p -> PassengerResponse.from(p, nicknameMap.getOrDefault(p.getPassengerId(), "승객")))
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
