package com.example.llm.asset.processing.parse;

import java.io.InputStream;

public interface AssetContentExtractor {
    ExtractedContent extract(InputStream content, String mimeType, long sizeBytes);

    record ExtractedContent(String text, Integer pageCount, String language) {
    }
}
