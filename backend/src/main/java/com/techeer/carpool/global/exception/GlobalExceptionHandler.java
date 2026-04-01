package com.techeer.carpool.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CarpoolException.class)
    public ResponseEntity<Map<String, String>> handleCarpoolException(CarpoolException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(Map.of("code", errorCode.getCode(), "message", errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception e) {
        return ResponseEntity
                .status(500)
                .body(Map.of("code", "SERVER_ERROR", "message", "서버 오류가 발생했습니다."));
    }
}
