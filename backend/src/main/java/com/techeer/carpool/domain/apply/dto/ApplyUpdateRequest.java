package com.techeer.carpool.domain.apply.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApplyUpdateRequest {

    private boolean accepted;
    private String rejectReason;
}