package com.techeer.carpool.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@MappedSuperclass
@Getter
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(nullable = false)
    private boolean deleted = false;

    public void delete() {
        this.deleted = true;
    }
}
