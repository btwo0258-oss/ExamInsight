package com.example.llm.asset.processing;

public class ProcessingFailure extends RuntimeException {
    private final String code;
    private final boolean retryable;

    private ProcessingFailure(String code, String safeMessage, boolean retryable, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public static ProcessingFailure retryable(String code, String safeMessage, Throwable cause) {
        return new ProcessingFailure(code, safeMessage, true, cause);
    }

    public static ProcessingFailure terminal(String code, String safeMessage) {
        return new ProcessingFailure(code, safeMessage, false, null);
    }

    public static ProcessingFailure terminal(String code, String safeMessage, Throwable cause) {
        return new ProcessingFailure(code, safeMessage, false, cause);
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
