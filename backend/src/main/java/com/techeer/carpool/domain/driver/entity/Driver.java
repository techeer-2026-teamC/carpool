package com.techeer.carpool.domain.driver.entity;

import com.techeer.carpool.global.common.entity.SoftDeletableEntity;
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
}
