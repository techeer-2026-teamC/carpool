package com.techeer.carpool.global.exception;

import lombok.Getter;

@Getter
public class CarpoolException extends RuntimeException {

    private final ErrorCode errorCode;

    public CarpoolException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
