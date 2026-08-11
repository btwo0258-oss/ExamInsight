package com.example.llm.asset.processing.parse;

import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class StructuredTextChunker {
    private static final int MAX_UTF8_BYTES = 65_536;
    private final int targetTokens;
    private final int overlapTokens;
    private final int maxTokens;

    public StructuredTextChunker(AssetProcessingProperties properties) {
        this.targetTokens = properties.getParser().getTargetTokens();
        this.overlapTokens = properties.getParser().getOverlapTokens();
        this.maxTokens = properties.getParser().getMaxTokens();
        if (targetTokens > maxTokens || overlapTokens >= targetTokens) {
            throw new IllegalStateException("Invalid V2 text chunking limits");
        }
    }

    public List<TextChunk> split(String text) {
        if (text == null || text.isBlank()) {
            throw ProcessingFailure.terminal("NO_EXTRACTABLE_TEXT", "文件中没有可用于学习的文本内容。");
        }
        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = advanceByTokens(text, start, maxTokens);
            int targetEnd = advanceByTokens(text, start, targetTokens);
            int end = targetEnd >= text.length() ? text.length() : bestBoundary(text, start, targetEnd, hardEnd);
            if (end <= start) {
                end = hardEnd;
            }
            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                int tokens = estimateTokens(content);
                if (tokens > maxTokens || content.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
                    throw ProcessingFailure.terminal("CHUNK_LIMIT_EXCEEDED", "文本切片超过安全处理上限。");
                }
                chunks.add(new TextChunk(chunks.size() + 1, content, tokens));
            }
            if (end >= text.length()) {
                break;
            }
            int overlapStart = retreatByTokens(text, end, overlapTokens);
            start = overlapStart > start ? overlapStart : end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        if (chunks.isEmpty()) {
            throw ProcessingFailure.terminal("NO_EXTRACTABLE_TEXT", "文件中没有可用于学习的文本内容。");
        }
        return List.copyOf(chunks);
    }

    public int estimateTokens(String text) {
        int tokens = 0;
        int latinRun = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (isCjk(codePoint)) {
                tokens++;
                latinRun = 0;
            } else if (Character.isLetterOrDigit(codePoint)) {
                if (latinRun % 4 == 0) {
                    tokens++;
                }
                latinRun++;
            } else {
                latinRun = 0;
                if (!Character.isWhitespace(codePoint)) {
                    tokens++;
                }
            }
        }
        return Math.max(tokens, 1);
    }

    private int advanceByTokens(String text, int start, int limit) {
        int tokens = 0;
        int latinRun = 0;
        int index = start;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            boolean latin = Character.isLetterOrDigit(codePoint) && !isCjk(codePoint);
            boolean increments = isCjk(codePoint)
                    || (!Character.isWhitespace(codePoint) && !latin)
                    || (latin && latinRun % 4 == 0);
            if (increments && tokens >= limit) {
                break;
            }
            if (increments) {
                tokens++;
            }
            latinRun = latin ? latinRun + 1 : 0;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private int retreatByTokens(String text, int end, int limit) {
        int index = end;
        int tokens = 0;
        int latinRun = 0;
        while (index > 0 && tokens < limit) {
            int codePoint = text.codePointBefore(index);
            index -= Character.charCount(codePoint);
            boolean latin = Character.isLetterOrDigit(codePoint) && !isCjk(codePoint);
            if (isCjk(codePoint)
                    || (!Character.isWhitespace(codePoint) && !latin)
                    || (latin && latinRun % 4 == 0)) {
                tokens++;
            }
            latinRun = latin ? latinRun + 1 : 0;
        }
        return index;
    }

    private int bestBoundary(String text, int start, int targetEnd, int hardEnd) {
        int forward = Math.min(hardEnd, text.length());
        for (int index = targetEnd; index < forward; index++) {
            char current = text.charAt(index);
            if (current == '\n' || current == '。' || current == '！' || current == '？'
                    || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        for (int index = targetEnd - 1; index > start; index--) {
            char current = text.charAt(index);
            if (current == '\n' || current == '。' || current == '！' || current == '？'
                    || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        return targetEnd;
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    public record TextChunk(int sequence, String content, int tokenCount) {
    }
}
