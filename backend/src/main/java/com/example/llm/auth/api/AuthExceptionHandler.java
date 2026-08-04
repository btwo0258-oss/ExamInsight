package com.example.llm.auth.api;

import com.example.llm.auth.security.AuthCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.example.llm.auth")
public class AuthExceptionHandler {
    private final AuthCrypto crypto;

    public AuthExceptionHandler(AuthCrypto crypto) {
        this.crypto = crypto;
    }

    @ExceptionHandler(AuthApiException.class)
    public ResponseEntity<AuthDtos.ErrorEnvelope> handleAuthException(AuthApiException exception) {
        return error(exception.status(), exception.code(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthDtos.ErrorEnvelope> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不符合要求。", Map.of("fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthDtos.ErrorEnvelope> handleUnreadable(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "请求正文格式不正确。", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthDtos.ErrorEnvelope> handleUnexpected(Exception exception) {
        String requestId = crypto.newExternalId();
        log.error("Unexpected V2 authentication error, requestId={}", requestId, exception);
        AuthDtos.ErrorBody body = new AuthDtos.ErrorBody(
                "INTERNAL_ERROR", "系统繁忙，请稍后重试。", requestId, Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AuthDtos.ErrorEnvelope(body));
    }

    private ResponseEntity<AuthDtos.ErrorEnvelope> error(
            HttpStatus status, String code, String message, Map<String, Object> details) {
        AuthDtos.ErrorBody body = new AuthDtos.ErrorBody(
                code, message, crypto.newExternalId(), details);
        return ResponseEntity.status(status).body(new AuthDtos.ErrorEnvelope(body));
    }
}
