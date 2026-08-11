package com.example.llm.asset.processing.parse;

import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.integration.ai.ProviderCallException;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class TikaOcrContentExtractor implements AssetContentExtractor {
    private static final List<String> PAGE_COUNT_KEYS = List.of(
            "xmpTPg:NPages", "meta:page-count", "Page-Count", "pageCount");

    private final AssetProcessingProperties properties;
    private final AiCapabilityRouter aiCapabilities;

    public TikaOcrContentExtractor(
            AssetProcessingProperties properties,
            AiCapabilityRouter aiCapabilities) {
        this.properties = properties;
        this.aiCapabilities = aiCapabilities;
    }

    @Override
    public ExtractedContent extract(InputStream content, String mimeType, long sizeBytes) {
        String rawText;
        Integer pageCount;
        if (mimeType != null && mimeType.startsWith("image/")) {
            try {
                byte[] image = content.readNBytes(Math.toIntExact(sizeBytes) + 1);
                if (image.length != sizeBytes) {
                    throw ProcessingFailure.terminal("FILE_CONTENT_MISMATCH", "文件内容不完整，请重新上传。");
                }
                rawText = aiCapabilities.recognize(image, mimeType).value();
                pageCount = 1;
            } catch (ProcessingFailure failure) {
                throw failure;
            } catch (ProviderCallException exception) {
                throw exception.retryable()
                        ? ProcessingFailure.retryable(
                                "OCR_UNAVAILABLE", "图片文字识别服务暂时不可用。", exception)
                        : ProcessingFailure.terminal(
                                "OCR_PROVIDER_REJECTED", "图片文字识别服务拒绝了本次请求，请检查模型配置。", exception);
            } catch (IllegalStateException exception) {
                throw ProcessingFailure.retryable("OCR_UNAVAILABLE", "图片文字识别服务暂时不可用。", exception);
            } catch (IOException | ArithmeticException exception) {
                throw ProcessingFailure.terminal("FILE_PARSE_FAILED", "文件解析失败，请检查文件是否完整。", exception);
            }
        } else {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, mimeType);
            Tika tika = new Tika();
            tika.setMaxStringLength(properties.getParser().getMaxCharacters());
            try {
                rawText = tika.parseToString(content, metadata);
                pageCount = readPageCount(metadata);
            } catch (ProcessingFailure failure) {
                throw failure;
            } catch (Exception exception) {
                throw ProcessingFailure.terminal(
                        "FILE_PARSE_FAILED",
                        "文件解析失败或文本内容超过处理上限，请检查文件后重试。",
                        exception);
            }
        }

        String normalized = normalize(rawText);
        if (normalized.isBlank()) {
            throw ProcessingFailure.terminal("NO_EXTRACTABLE_TEXT", "文件中没有可用于学习的文本内容。");
        }
        return new ExtractedContent(normalized, pageCount, detectLanguage(normalized));
    }

    private Integer readPageCount(Metadata metadata) {
        for (String key : PAGE_COUNT_KEYS) {
            String value = metadata.get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                int pages = Integer.parseInt(value.trim());
                if (pages > 1000) {
                    throw ProcessingFailure.terminal("TOO_MANY_PAGES", "文件页数超过 1000 页处理上限。");
                }
                if (pages > 0) {
                    return pages;
                }
            } catch (NumberFormatException ignored) {
                // Untrusted metadata is optional; malformed page counts do not block text parsing.
            }
        }
        return null;
    }

    private String normalize(String text) {
        String normalized = text == null ? "" : text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0000', ' ');
        normalized = normalized.replaceAll("[\\t\\x0B\\f ]+", " ");
        normalized = normalized.replaceAll(" *\\n *", "\n");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }

    private String detectLanguage(String text) {
        long cjk = text.codePoints().filter(this::isCjk).count();
        long latin = text.codePoints().filter(Character::isLetter).filter(codePoint -> !isCjk(codePoint)).count();
        if (cjk > 0 && latin > 0) {
            return "mixed";
        }
        return cjk > 0 ? "zh" : "en";
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
