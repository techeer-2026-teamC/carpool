package com.techeer.carpool.domain.review.service;

import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RideStatus;
import com.techeer.carpool.domain.ride.repository.RideRepository;
import com.techeer.carpool.domain.review.dto.ReviewCreateRequest;
import com.techeer.carpool.domain.review.dto.ReviewResponse;
import com.techeer.carpool.domain.review.entity.Review;
import com.techeer.carpool.domain.review.repository.ReviewRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewCreateService {

    private final ReviewRepository reviewRepository;
    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;
    private final CarpoolMetrics carpoolMetrics;

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 1.5, random = true)
    )
    @Transactional
    public ReviewResponse createReview(Long rideId, ReviewCreateRequest request, Long reviewerId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.RIDE_NOT_FOUND));

        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new CarpoolException(ErrorCode.REVIEW_RIDE_NOT_COMPLETED);
        }

        boolean isPassenger = ride.getPassengers().stream()
                .anyMatch(p -> p.getPassengerId().equals(reviewerId));
        if (!isPassenger) {
            throw new CarpoolException(ErrorCode.REVIEW_FORBIDDEN);
        }

        if (reviewRepository.existsByRideIdAndReviewerId(rideId, reviewerId)) {
            throw new CarpoolException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review;
        try {
            review = reviewRepository.save(Review.builder()
                    .rideId(rideId)
                    .reviewerId(reviewerId)
                    .driverId(ride.getDriverId())
                    .rating(request.getRating())
                    .comment(request.getComment())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new CarpoolException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Driver driver = driverRepository.findByMemberIdAndDeletedFalseWithLock(ride.getDriverId())
                .orElseThrow(() -> new CarpoolException(ErrorCode.DRIVER_NOT_FOUND));
        driver.addRating(request.getRating());
        carpoolMetrics.incrementReviewCreated();

        return ReviewResponse.from(review);
    }
}
