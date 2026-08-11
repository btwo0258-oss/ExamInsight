package com.example.llm.integration.ai;

public class ProviderCallException extends RuntimeException {

    public enum Category {
        BAD_REQUEST,
        UNSUPPORTED_INPUT,
        CONTENT_SAFETY,
        AUTHENTICATION,
        QUOTA_EXHAUSTED,
        RATE_LIMITED,
        TIMEOUT,
        UNAVAILABLE,
        INVALID_RESPONSE,
        INTERRUPTED
    }

    private final String provider;
    private final String model;
    private final String code;
    private final Category category;
    private final boolean retryable;

    public ProviderCallException(
            String provider,
            String model,
            String code,
            Category category,
            boolean retryable,
            String message,
            Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.model = model;
        this.code = code;
        this.category = category;
        this.retryable = retryable;
    }

    public String provider() { return provider; }
    public String model() { return model; }
    public String code() { return code; }
    public Category category() { return category; }
    public boolean retryable() { return retryable; }
}
