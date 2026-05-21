package com.techeer.carpool.domain.review.service;

import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.review.dto.ReviewResponse;
import com.techeer.carpool.domain.review.repository.ReviewRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;
    private final DriverRepository driverRepository;

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(Long reviewerId) {
        return reviewRepository.findAllByReviewerId(reviewerId).stream()
                .map(ReviewResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> getMyReviewForRide(Long rideId, Long reviewerId) {
        return reviewRepository.findByRideIdAndReviewerId(rideId, reviewerId)
                .map(ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public double getDriverRating(Long driverId) {
        Driver driver = driverRepository.findByMemberIdAndDeletedFalse(driverId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.DRIVER_NOT_FOUND));
        return driver.getAverageRating();
    }
}
