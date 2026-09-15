package com.techeer.carpool.domain.application.entity;

import com.techeer.carpool.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(
    name = "applications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "applicant_id"})
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Application extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Builder
    public Application(Long postId, Long applicantId) {
        this.postId = postId;
        this.applicantId = applicantId;
    }

    public void accept() {
        this.status = ApplicationStatus.ACCEPTED;
    }

    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    public void cancel() { this.status = ApplicationStatus.CANCELLED; }

    public void resetToPending() {
        this.status = ApplicationStatus.PENDING;
    }
}
