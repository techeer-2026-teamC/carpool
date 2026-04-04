package com.techeer.carpool.domain.apply.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "applies")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Apply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplyStatus status;

    @Column
    private String rejectReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = ApplyStatus.PENDING;
        this.deleted = false;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public Apply(Long postId, Long memberId) {
        this.postId = postId;
        this.memberId = memberId;
    }

    public void accept() {
        this.status = ApplyStatus.ACCEPTED;
    }

    public void reject(String rejectReason) {
        this.status = ApplyStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    public void delete() {
        this.deleted = true;
    }
}