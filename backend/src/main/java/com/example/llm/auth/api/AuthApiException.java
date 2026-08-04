package com.example.llm.auth.api;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class AuthApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public AuthApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public AuthApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
