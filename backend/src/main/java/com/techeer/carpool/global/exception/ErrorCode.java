package com.techeer.carpool.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    POST_NOT_FOUND("POST_001", "게시글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    POST_FULL("POST_002", "카풀 인원이 가득 찼습니다.", HttpStatus.CONFLICT),
    INVALID_INPUT("COMMON_001", "잘못된 입력값입니다.", HttpStatus.BAD_REQUEST),
    APPLY_NOT_FOUND("APPLY_001", "신청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    APPLY_ALREADY_EXISTS("APPLY_002", "이미 신청한 게시글입니다.", HttpStatus.CONFLICT),
    APPLY_ALREADY_PROCESSED("APPLY_003", "이미 처리된 신청입니다.", HttpStatus.BAD_REQUEST),
    APPLY_SELF_NOT_ALLOWED("APPLY_004", "본인 게시글에 신청할 수 없습니다.", HttpStatus.BAD_REQUEST);

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
