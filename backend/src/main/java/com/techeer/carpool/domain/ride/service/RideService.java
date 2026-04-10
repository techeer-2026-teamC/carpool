package com.techeer.carpool.domain.ride.service;

import com.techeer.carpool.domain.ride.dto.RideDto;
import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RidePassenger;
import com.techeer.carpool.domain.ride.repository.RidePassengerRepository;
import com.techeer.carpool.domain.ride.repository.RideRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service                    // 이 클래스가 스프링의 서비스 빈임을 선언, 스프링이 자동으로 관리
@RequiredArgsConstructor    // Lombok: final 필드를 받는 생성자 자동 생성 → 생성자 주입 방식으로 의존성 주입
@Transactional(readOnly = true)
// 클래스 전체에 읽기 전용 트랜잭션 적용
// readOnly=true: 조회 전용 메서드에 최적화 (불필요한 변경 감지 생략 → 성능 향상)
// 데이터 변경이 필요한 메서드는 개별적으로 @Transactional을 따로 붙여서 덮어씀
public class RideService {

    private final RideRepository rideRepository;
    private final RidePassengerRepository ridePassengerRepository;

    // 운행 생성
    @Transactional  // 데이터 변경(INSERT)이 있으므로 readOnly=false인 트랜잭션으로 덮어씀
    public RideDto.RideResponse createRide(RideDto.CreateRequest request) {
        // 요청 데이터로 Ride 엔티티 생성
        Ride ride = Ride.builder()
                .postId(request.getPostId())
                .driverId(request.getDriverId())
                .build();
        // DB에 저장 후, 저장된 엔티티를 Response DTO로 변환해서 반환
        return RideDto.RideResponse.from(rideRepository.save(ride));
    }

    // 운행 조회 (readOnly 트랜잭션 그대로 사용)
    public RideDto.RideResponse getRide(Long rideId) {
        return RideDto.RideResponse.from(findRideById(rideId));
    }

    // 운행 시작
    @Transactional
    public RideDto.RideResponse startRide(Long rideId) {
        Ride ride = findRideById(rideId);
        ride.start();  // 엔티티 내부 메서드로 상태 변경 → 트랜잭션 종료 시 DB 자동 반영 (변경 감지)
        return RideDto.RideResponse.from(ride);
    }

    // 운행 종료
    @Transactional
    public RideDto.RideResponse completeRide(Long rideId) {
        Ride ride = findRideById(rideId);
        ride.complete();  // 상태를 COMPLETED로 변경
        return RideDto.RideResponse.from(ride);
    }

    // 운전자 위치 업데이트
    // 브라우저의 Geolocation API가 주기적으로 이 API를 호출해서 위치를 갱신
    @Transactional
    public RideDto.LocationResponse updateLocation(Long rideId, RideDto.LocationUpdateRequest request) {
        Ride ride = findRideById(rideId);
        ride.updateLocation(request.getLatitude(), request.getLongitude());  // 위도/경도 갱신
        return RideDto.LocationResponse.from(ride);
    }

    // 현재 위치 조회 (탑승자가 드라이버 위치를 확인할 때 사용)
    public RideDto.LocationResponse getLocation(Long rideId) {
        return RideDto.LocationResponse.from(findRideById(rideId));
    }

    // 탑승자 목록 조회
    public List<RideDto.PassengerResponse> getPassengers(Long rideId) {
        Ride ride = findRideById(rideId);
        return ride.getPassengers().stream()          // 탑승자 리스트를 스트림으로 변환
                .map(RideDto.PassengerResponse::from)  // 각 RidePassenger를 PassengerResponse DTO로 변환
                .collect(Collectors.toList());         // 다시 리스트로 모음
    }

    // 탑승 확인 (드라이버가 특정 탑승자의 탑승을 확인)
    @Transactional
    public RideDto.PassengerResponse boardPassenger(Long rideId, Long applicationId) {
        RidePassenger passenger = findPassenger(rideId, applicationId);
        passenger.board();  // 상태를 BOARDED로 변경
        return RideDto.PassengerResponse.from(passenger);
    }

    // 하차 확인 (드라이버가 특정 탑승자의 하차를 확인)
    @Transactional
    public RideDto.PassengerResponse dropOffPassenger(Long rideId, Long applicationId) {
        RidePassenger passenger = findPassenger(rideId, applicationId);
        passenger.dropOff();  // 상태를 DROPPED_OFF로 변경
        return RideDto.PassengerResponse.from(passenger);
    }

    // ── 내부 헬퍼 메서드 ───────────────────────────────────
    // 중복 코드를 줄이기 위해 공통 조회 로직을 분리

    // rideId로 Ride 조회, 없으면 404 예외 발생
    private Ride findRideById(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new EntityNotFoundException("운행을 찾을 수 없습니다. id=" + rideId));
        // Optional.orElseThrow(): 값이 있으면 반환, 없으면 예외 던짐
    }

    // rideId + applicationId로 RidePassenger 조회, 없으면 404 예외 발생
    private RidePassenger findPassenger(Long rideId, Long applicationId) {
        return ridePassengerRepository.findByRideIdAndApplicationId(rideId, applicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "탑승자를 찾을 수 없습니다. rideId=" + rideId + ", applicationId=" + applicationId));
    }
}