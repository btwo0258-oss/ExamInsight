package com.example.llm.asset.api;

import com.example.llm.auth.security.AuthCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.example.llm.asset")
public class AssetExceptionHandler {
    private final AuthCrypto crypto;

    public AssetExceptionHandler(AuthCrypto crypto) {
        this.crypto = crypto;
    }

    @ExceptionHandler(AssetApiException.class)
    public ResponseEntity<ErrorEnvelope> handleAssetException(AssetApiException exception) {
        return error(exception.status(), exception.code(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不符合要求。", Map.of("fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleUnreadable(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "请求正文格式不正确。", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PATH_PARAMETER", "请求路径参数格式不正确。", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_CONTENT_TYPE",
                "上传分片必须使用 application/octet-stream。", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception) {
        String requestId = crypto.newExternalId();
        log.error("Unexpected V2 asset error, requestId={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorEnvelope(new ErrorBody(
                        "INTERNAL_ERROR", "系统繁忙，请稍后重试。", requestId, Map.of())));
    }

    private ResponseEntity<ErrorEnvelope> error(
            HttpStatus status, String code, String message, Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(
                new ErrorBody(code, message, crypto.newExternalId(), details)));
    }

    public record ErrorEnvelope(ErrorBody error) {
    }

    public record ErrorBody(String code, String message, String requestId, Map<String, Object> details) {
    }
}
