package com.example.llm.asset.security;

import com.example.llm.asset.api.AssetApiException;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class FileTypePolicy {
    private static final long MIB = 1024L * 1024L;
    private static final long TEXT_LIMIT = 10 * MIB;
    private static final long IMAGE_LIMIT = 20 * MIB;
    private static final long DOCUMENT_LIMIT = 100 * MIB;
    private static final long OFFICE_UNCOMPRESSED_LIMIT = 500 * MIB;
    private static final int OFFICE_ENTRY_LIMIT = 10_000;
    private static final long OFFICE_COMPRESSION_RATIO_LIMIT = 100;

    private static final Map<String, FileRule> RULES = rules();
    private final Tika tika = new Tika();

    public DeclaredFile validateDeclaration(
            String originalFilename,
            String declaredMime,
            long expectedSize) {
        String safeName = validateFilename(originalFilename);
        String extension = extensionOf(safeName);
        FileRule rule = RULES.get(extension);
        if (rule == null) {
            throw new AssetApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_FILE_TYPE",
                    "暂不支持该文件类型，请上传 PDF、DOCX、PPTX、XLSX、TXT、MD、CSV、JPG、PNG 或 WebP 文件。",
                    Map.of("extension", extension.isEmpty() ? "none" : extension));
        }
        if (expectedSize < 1 || expectedSize > rule.maximumSize()) {
            throw new AssetApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "文件大小超过该类型的上传限制。",
                    Map.of("maximumBytes", rule.maximumSize()));
        }

        String normalizedDeclaredMime = normalizeMime(declaredMime);
        if (normalizedDeclaredMime != null
                && !normalizedDeclaredMime.equals("application/octet-stream")
                && !rule.compatibleMimes().contains(normalizedDeclaredMime)) {
            throw new AssetApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "DECLARED_MIME_MISMATCH",
                    "文件扩展名与浏览器声明的文件类型不一致。",
                    Map.of("extension", extension, "declaredMime", normalizedDeclaredMime));
        }
        return new DeclaredFile(safeName, extension, normalizedDeclaredMime, rule.canonicalMime());
    }

    public InspectedFile inspect(
            DeclaredFile declaration,
            long actualSize,
            InputStreamSupplier contentSupplier) throws IOException {
        FileRule rule = RULES.get(declaration.extension());
        if (rule == null || actualSize < 1 || actualSize > rule.maximumSize()) {
            throw new AssetApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "FILE_TYPE_REJECTED", "文件不符合允许的上传类型。", Map.of());
        }

        if (rule.officeRoot() != null) {
            inspectOfficePackage(contentSupplier, rule.officeRoot(), actualSize);
        }

        String detectedMime;
        try (InputStream content = contentSupplier.open()) {
            detectedMime = normalizeMime(tika.detect(content, declaration.originalFilename()));
        }
        if (!isDetectedMimeCompatible(rule, detectedMime)) {
            throw new AssetApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "FILE_CONTENT_MISMATCH",
                    "文件内容与扩展名不一致，已拒绝上传。",
                    Map.of("extension", declaration.extension(),
                            "detectedMime", detectedMime == null ? "unknown" : detectedMime));
        }
        return new InspectedFile(rule.canonicalMime(), detectedMime);
    }

    private void inspectOfficePackage(
            InputStreamSupplier supplier,
            String requiredRoot,
            long compressedSize) throws IOException {
        int entries = 0;
        long totalUncompressed = 0;
        boolean contentTypesFound = false;
        boolean requiredRootFound = false;
        byte[] buffer = new byte[64 * 1024];

        try (ZipInputStream zip = new ZipInputStream(supplier.open())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > OFFICE_ENTRY_LIMIT) {
                    rejectOfficePackage("Office 文件包含过多内部条目。");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
                    rejectOfficePackage("Office 文件包含不安全的内部路径。");
                }
                if (name.equals("[Content_Types].xml")) {
                    contentTypesFound = true;
                }
                if (name.startsWith(requiredRoot)) {
                    requiredRootFound = true;
                }
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    totalUncompressed += read;
                    if (totalUncompressed > OFFICE_UNCOMPRESSED_LIMIT
                            || totalUncompressed > compressedSize * OFFICE_COMPRESSION_RATIO_LIMIT) {
                        rejectOfficePackage("Office 文件解压后的体积异常，已拒绝上传。");
                    }
                }
                zip.closeEntry();
            }
        } catch (AssetApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AssetApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "INVALID_OFFICE_PACKAGE",
                    "Office 文件已损坏或不是有效的现代 Office 格式。",
                    Map.of());
        }

        if (!contentTypesFound || !requiredRootFound) {
            rejectOfficePackage("Office 文件结构与扩展名不一致。");
        }
    }

    private void rejectOfficePackage(String message) {
        throw new AssetApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "INVALID_OFFICE_PACKAGE",
                message,
                Map.of());
    }

    private boolean isDetectedMimeCompatible(FileRule rule, String detectedMime) {
        if (detectedMime == null) {
            return false;
        }
        if (rule.compatibleMimes().contains(detectedMime)) {
            return true;
        }
        return rule.officeRoot() != null
                && (detectedMime.equals("application/zip")
                || detectedMime.equals("application/x-tika-ooxml")
                || detectedMime.equals("application/octet-stream"));
    }

    private String validateFilename(String originalFilename) {
        if (originalFilename == null) {
            throw invalidFilename();
        }
        String trimmed = originalFilename.trim();
        if (trimmed.isEmpty() || trimmed.length() > 255
                || trimmed.contains("/") || trimmed.contains("\\")
                || trimmed.equals(".") || trimmed.equals("..")) {
            throw invalidFilename();
        }
        for (int index = 0; index < trimmed.length(); index++) {
            if (Character.isISOControl(trimmed.charAt(index))) {
                throw invalidFilename();
            }
        }
        return trimmed;
    }

    private AssetApiException invalidFilename() {
        return new AssetApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_FILENAME",
                "文件名为空、过长或包含不安全字符。",
                Map.of());
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return null;
        }
        String normalized = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 160 || !normalized.matches("[a-z0-9!#$&^_.+\\-]+/[a-z0-9!#$&^_.+\\-]+")) {
            throw new AssetApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MIME_TYPE",
                    "文件类型声明格式不正确。",
                    Map.of());
        }
        return normalized;
    }

    private static Map<String, FileRule> rules() {
        Map<String, FileRule> rules = new HashMap<>();
        rules.put("pdf", rule("application/pdf", DOCUMENT_LIMIT, null, "application/pdf"));
        rules.put("docx", rule(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DOCUMENT_LIMIT, "word/",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        rules.put("xlsx", rule(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                DOCUMENT_LIMIT, "xl/",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        rules.put("pptx", rule(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                DOCUMENT_LIMIT, "ppt/",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        rules.put("txt", rule("text/plain", TEXT_LIMIT, null, "text/plain"));
        rules.put("md", rule("text/markdown", TEXT_LIMIT, null,
                "text/markdown", "text/plain", "text/x-web-markdown"));
        rules.put("csv", rule("text/csv", TEXT_LIMIT, null,
                "text/csv", "text/plain", "application/csv", "text/comma-separated-values"));
        rules.put("jpg", rule("image/jpeg", IMAGE_LIMIT, null, "image/jpeg"));
        rules.put("jpeg", rule("image/jpeg", IMAGE_LIMIT, null, "image/jpeg"));
        rules.put("png", rule("image/png", IMAGE_LIMIT, null, "image/png"));
        rules.put("webp", rule("image/webp", IMAGE_LIMIT, null, "image/webp"));
        return Map.copyOf(rules);
    }

    private static FileRule rule(
            String canonicalMime,
            long maximumSize,
            String officeRoot,
            String... compatibleMimes) {
        Set<String> mimes = new HashSet<>(Set.of(compatibleMimes));
        mimes.add(canonicalMime);
        return new FileRule(canonicalMime, maximumSize, Set.copyOf(mimes), officeRoot);
    }

    public record DeclaredFile(
            String originalFilename,
            String extension,
            String declaredMime,
            String canonicalMime) {
    }

    public record InspectedFile(String canonicalMime, String detectedMime) {
    }

    private record FileRule(
            String canonicalMime,
            long maximumSize,
            Set<String> compatibleMimes,
            String officeRoot) {
    }

    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws IOException;
    }
}
