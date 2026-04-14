package com.techeer.carpool.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    POST_NOT_FOUND("POST_001", "게시글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_INPUT("COMMON_001", "잘못된 입력값입니다.", HttpStatus.BAD_REQUEST),

    EMAIL_DUPLICATE("AUTH_001", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    MEMBER_NOT_FOUND("AUTH_002", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS("AUTH_003", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("AUTH_004", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN("AUTH_005", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),

    DRIVER_ALREADY_REGISTERED("DRIVER_001", "이미 운전자로 등록되어 있습니다.", HttpStatus.CONFLICT),
    CAR_MODEL_NOT_FOUND("DRIVER_002", "차량 모델을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CAR_COLOR_NOT_FOUND("DRIVER_003", "차량 색상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CAR_NUMBER_DUPLICATE("DRIVER_004", "이미 등록된 차량 번호입니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public HttpStatus getStatus() { return status; }
}
