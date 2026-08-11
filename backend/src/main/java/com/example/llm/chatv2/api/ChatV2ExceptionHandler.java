package com.example.llm.chatv2.api;

import com.example.llm.auth.security.AuthCrypto;
import jakarta.validation.ConstraintViolationException;
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
@RestControllerAdvice(basePackages = "com.example.llm.chatv2")
public class ChatV2ExceptionHandler {
    private final AuthCrypto crypto;

    public ChatV2ExceptionHandler(AuthCrypto crypto) {
        this.crypto = crypto;
    }

    @ExceptionHandler(ChatV2ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleChatError(ChatV2ApiException exception) {
        return error(exception.status(), exception.code(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不符合要求。",
                Map.of("fields", fields));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorEnvelope> handleInvalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式不正确。", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception) {
        String requestId = crypto.newExternalId();
        log.error("Unexpected V2 chat error, requestId={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorEnvelope(new ErrorBody(
                        "INTERNAL_ERROR", "系统繁忙，请稍后重试。", requestId, Map.of())));
    }

    private ResponseEntity<ErrorEnvelope> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(
                new ErrorBody(code, message, crypto.newExternalId(), details)));
    }

    public record ErrorEnvelope(ErrorBody error) {
    }

    public record ErrorBody(String code, String message, String requestId, Map<String, Object> details) {
    }
}
