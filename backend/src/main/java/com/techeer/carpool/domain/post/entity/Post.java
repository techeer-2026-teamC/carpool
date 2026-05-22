package com.techeer.carpool.domain.post.entity;

import com.techeer.carpool.global.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_status_departure_time", columnList = "status, departure_time")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Post extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

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

    private Integer price;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>();

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
                String description, boolean autoAccept,
                Integer price, List<Tag> tags) {
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
        this.price = price;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void close() {
        if (this.status == PostStatus.CLOSED) {
            throw new IllegalStateException("이미 마감된 게시글입니다.");
        }
        this.status = PostStatus.CLOSED;
    }

    public boolean isFull() {
        return this.currentPassengers >= this.maxPassengers;
    }

    public void incrementPassengers() {
        this.currentPassengers++;
        if (this.currentPassengers >= this.maxPassengers) {
            this.status = PostStatus.CLOSED;
        }
    }

    public void decrementPassengers() {
        if (this.currentPassengers > 0) {
            this.currentPassengers--;
        }
        if (this.status == PostStatus.CLOSED && this.currentPassengers < this.maxPassengers) {
            this.status = PostStatus.OPEN;
        }
    }

    public void refreshDepartureTime(LocalDateTime time) {
        this.departureTime = time;
    }

    public void updateFrom(PostUpdateCommand command) {
        if (command.title() != null) this.title = command.title();
        if (command.departureLocation() != null) this.departureLocation = command.departureLocation();
        if (command.departureLat() != null) this.departureLat = command.departureLat();
        if (command.departureLng() != null) this.departureLng = command.departureLng();
        if (command.destinationLocation() != null) this.destinationLocation = command.destinationLocation();
        if (command.destinationLat() != null) this.destinationLat = command.destinationLat();
        if (command.destinationLng() != null) this.destinationLng = command.destinationLng();
        if (command.departureTime() != null) this.departureTime = command.departureTime();
        if (command.maxPassengers() > 0) this.maxPassengers = command.maxPassengers();
        if (command.description() != null) this.description = command.description();
        if (command.status() != null) this.status = command.status();
        if (command.price() != null) this.price = command.price();
        this.autoAccept = command.autoAccept();
        if (command.tags() != null) {
            Set<Long> currentIds = this.tags.stream().map(Tag::getId).collect(Collectors.toSet());
            Set<Long> newIds = command.tags().stream().map(Tag::getId).collect(Collectors.toSet());
            if (!currentIds.equals(newIds)) {
                this.tags = new ArrayList<>(command.tags());
            }
        }
    }
}
