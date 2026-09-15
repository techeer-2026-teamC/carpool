package com.techeer.carpool.domain.post.entity;

import com.techeer.carpool.global.common.entity.SoftDeletableEntity;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostType type = PostType.CARPOOL;

    @Column(nullable = false)
    private boolean manuallyClosed;

    private LocalDateTime meetingCompletedAt;
    private LocalDateTime departureNotifiedAt;

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
        this.autoAccept = false;
    }

    @Builder
    public Post(Long memberId, String title,
                String departureLocation, Double departureLat, Double departureLng,
                String destinationLocation, Double destinationLat, Double destinationLng,
                LocalDateTime departureTime, int maxPassengers,
                String description, boolean autoAccept,
                Integer price, List<Tag> tags, PostType type) {
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
        this.autoAccept = false;
        this.type = type != null ? type : PostType.CARPOOL;
        this.status = PostStatus.OPEN;
        this.price = price;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void close() {
        this.status = PostStatus.CLOSED;
        this.manuallyClosed = true;
    }

    public int getCapacity() { return maxPassengers; }
    public int getOccupiedSeats() { return currentPassengers + (type == PostType.TAXI ? 1 : 0); }
    public int getAvailableSeats() { return Math.max(0, maxPassengers - getOccupiedSeats()); }
    public boolean isFull() { return getAvailableSeats() == 0; }

    public void requireBeforeCutoff(LocalDateTime now) {
        if (!now.isBefore(departureTime) || meetingCompletedAt != null) {
            throw new CarpoolException(ErrorCode.RECRUITMENT_CUTOFF);
        }
    }

    public void requireOpen(LocalDateTime now) {
        requireBeforeCutoff(now);
        if (status != PostStatus.OPEN || isFull()) {
            throw new CarpoolException(ErrorCode.APPLICATION_POST_FULL);
        }
    }

    public void incrementPassengers() {
        if (isFull()) throw new CarpoolException(ErrorCode.APPLICATION_POST_FULL);
        this.currentPassengers++;
        if (isFull()) this.status = PostStatus.CLOSED;
    }

    public void decrementPassengers() {
        if (currentPassengers <= 0) throw new CarpoolException(ErrorCode.POST_CONFLICT);
        this.currentPassengers--;
        refreshCapacityStatus();
    }

    private void refreshCapacityStatus() {
        if (!manuallyClosed && meetingCompletedAt == null && LocalDateTime.now().isBefore(departureTime)) {
            this.status = isFull() ? PostStatus.CLOSED : PostStatus.OPEN;
        }
    }

    public void completeMeeting(LocalDateTime at) {
        if (meetingCompletedAt == null) meetingCompletedAt = at.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        close();
    }

    public void markDepartureNotified(LocalDateTime at) { departureNotifiedAt = at; }

    public void validateUpdate(PostUpdateCommand c, PostType nextType, boolean hasApplications) {
        if (c.status() == PostStatus.CANCELLED) throw new CarpoolException(ErrorCode.INVALID_INPUT);
        if (hasApplications && (!Objects.equals(departureLocation, c.departureLocation())
                || !Objects.equals(departureLat, c.departureLat()) || !Objects.equals(departureLng, c.departureLng())
                || !Objects.equals(destinationLocation, c.destinationLocation())
                || !Objects.equals(destinationLat, c.destinationLat()) || !Objects.equals(destinationLng, c.destinationLng())
                || !Objects.equals(departureTime, c.departureTime()) || type != nextType
                || !Objects.equals(price, c.price()))) {
            throw new CarpoolException(ErrorCode.RECRUITMENT_FROZEN);
        }
        int occupied = currentPassengers + (nextType == PostType.TAXI ? 1 : 0);
        if (c.maxPassengers() < occupied || c.maxPassengers() < (nextType == PostType.TAXI ? 2 : 1)) {
            throw new CarpoolException(ErrorCode.POST_CAPACITY_INVALID);
        }
        if (c.status() == PostStatus.OPEN && manuallyClosed) {
            throw new CarpoolException(ErrorCode.POST_ALREADY_CLOSED);
        }
        this.type = nextType;
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
        if (command.status() == PostStatus.CLOSED) close();
        if (command.price() != null) this.price = command.price();
        this.autoAccept = false;
        refreshCapacityStatus();
        if (command.tags() != null) {
            Set<Long> currentIds = this.tags.stream().map(Tag::getId).collect(Collectors.toSet());
            Set<Long> newIds = command.tags().stream().map(Tag::getId).collect(Collectors.toSet());
            if (!currentIds.equals(newIds)) {
                this.tags = new ArrayList<>(command.tags());
            }
        }
    }
}
