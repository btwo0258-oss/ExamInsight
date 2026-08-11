package com.example.llm.asset.processing.security;

import java.io.InputStream;

public interface FileSecurityScanner {
    String scannerKey();

    String scannerVersion();

    ScanResult scan(InputStream content);

    record ScanResult(boolean clean, String threatName) {
        public static ScanResult cleanFile() {
            return new ScanResult(true, null);
        }

        public static ScanResult infected(String threatName) {
            return new ScanResult(false, threatName);
        }
    }
}
