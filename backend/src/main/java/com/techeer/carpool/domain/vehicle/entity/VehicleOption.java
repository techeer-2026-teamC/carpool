package com.techeer.carpool.domain.vehicle.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "vehicle_options")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class VehicleOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VehicleOptionType type;

    @Column(length = 50)
    private String brand;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 7)
    private String hexCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public VehicleOption(VehicleOptionType type, String brand, String name, String hexCode) {
        this.type = type;
        this.brand = brand;
        this.name = name;
        this.hexCode = hexCode;
    }
}
