package com.example.llm.asset.retrieval;

public class RetrievalException extends RuntimeException {
    private final String code;

    public RetrievalException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RetrievalException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
