package com.techeer.carpool.domain.driver.service;

import com.techeer.carpool.domain.driver.dto.DriverRegisterRequest;
import com.techeer.carpool.domain.driver.dto.DriverResponse;
import com.techeer.carpool.domain.driver.dto.DriverUpdateRequest;
import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public DriverResponse register(Long memberId, DriverRegisterRequest request) {
        memberRepository.findByIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));

        if (driverRepository.findByMemberIdAndDeletedFalse(memberId).isPresent()) {
            throw new CarpoolException(ErrorCode.DRIVER_ALREADY_REGISTERED);
        }

        if (driverRepository.existsByCarNumberAndDeletedFalse(request.getCarNumber())) {
            throw new CarpoolException(ErrorCode.CAR_NUMBER_DUPLICATE);
        }

        Driver driver = Driver.builder()
                .memberId(memberId)
                .carModel(request.getCarModel())
                .carColor(request.getCarColor())
                .carNumber(request.getCarNumber())
                .build();

        return DriverResponse.from(driverRepository.save(driver));
    }

    @Transactional(readOnly = true)
    public DriverResponse getMyDriver(Long memberId) {
        Driver driver = driverRepository.findByMemberIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.DRIVER_NOT_FOUND));

        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse update(Long memberId, DriverUpdateRequest request) {
        Driver driver = driverRepository.findByMemberIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.DRIVER_NOT_FOUND));

        if (!driver.getCarNumber().equals(request.getCarNumber())
                && driverRepository.existsByCarNumberAndDeletedFalse(request.getCarNumber())) {
            throw new CarpoolException(ErrorCode.CAR_NUMBER_DUPLICATE);
        }

        driver.update(request.getCarModel(), request.getCarColor(), request.getCarNumber());

        return DriverResponse.from(driver);
    }
}
