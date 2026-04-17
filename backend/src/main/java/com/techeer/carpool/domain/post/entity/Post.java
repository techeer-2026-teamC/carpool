package com.techeer.carpool.domain.post.entity;

import com.techeer.carpool.domain.post.dto.PostUpdateRequest;
import com.techeer.carpool.global.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Post extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String departureLocation;

    private Double departureLat;

    private Double departureLng;

    @Column(nullable = false, length = 100)
    private String destinationLocation;

    private Double destinationLat;

    private Double destinationLng;

    @Column(nullable = false)
    private LocalDateTime departureTime;

    @Column(nullable = false)
    private int maxPassengers;

    @Column(nullable = false)
    private int currentPassengers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean autoAccept;

    @PrePersist
    private void prePersist() {
        this.status = PostStatus.OPEN;
        this.currentPassengers = 0;
    }

    @Builder
    public Post(Long memberId, String title,
                String departureLocation, Double departureLat, Double departureLng,
                String destinationLocation, Double destinationLat, Double destinationLng,
                LocalDateTime departureTime, int maxPassengers,
                String description, boolean autoAccept) {
        this.memberId = memberId;
        this.title = title;
        this.departureLocation = departureLocation;
        this.departureLat = departureLat;
        this.departureLng = departureLng;
        this.destinationLocation = destinationLocation;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.departureTime = departureTime;
        this.maxPassengers = maxPassengers;
        this.description = description;
        this.autoAccept = autoAccept;
    }

    public void updateFrom(PostUpdateRequest request) {
        this.title = request.getTitle();
        this.departureLocation = request.getDepartureLocation();
        this.departureLat = request.getDepartureLat();
        this.departureLng = request.getDepartureLng();
        this.destinationLocation = request.getDestinationLocation();
        this.destinationLat = request.getDestinationLat();
        this.destinationLng = request.getDestinationLng();
        this.departureTime = request.getDepartureTime();
        this.maxPassengers = request.getMaxPassengers();
        this.description = request.getDescription();
        this.autoAccept = request.isAutoAccept();
        this.status = request.getStatus();
    }
}
