package com.techeer.carpool.domain.driver.entity;

import com.techeer.carpool.global.common.entity.SoftDeletableEntity;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "drivers")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Driver extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long driverId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 50)
    private String carModel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CarColor carColor;

    @Column(nullable = false, unique = true, length = 20)
    private String carNumber;

    @Column(nullable = false)
    private int totalRatingSum = 0;

    @Column(nullable = false)
    private int reviewCount = 0;

    @Builder
    public Driver(Long memberId, String carModel, CarColor carColor, String carNumber) {
        this.memberId = memberId;
        this.carModel = carModel;
        this.carColor = carColor;
        this.carNumber = carNumber;
    }

    public void update(String carModel, CarColor carColor, String carNumber) {
        this.carModel = carModel;
        this.carColor = carColor;
        this.carNumber = carNumber;
    }

    public void addRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new CarpoolException(ErrorCode.INVALID_INPUT);
        }
        this.totalRatingSum += rating;
        this.reviewCount++;
    }

    public double getAverageRating() {
        return reviewCount == 0 ? 0.0 : (double) totalRatingSum / reviewCount;
    }
}
