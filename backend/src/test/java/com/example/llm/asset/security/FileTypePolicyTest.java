package com.example.llm.asset.security;

import com.example.llm.asset.api.AssetApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTypePolicyTest {
    private final FileTypePolicy policy = new FileTypePolicy();

    @Test
    void acceptsTextAfterCheckingBothDeclarationAndContent() throws Exception {
        byte[] content = "ExamInsight study notes".getBytes(StandardCharsets.UTF_8);
        FileTypePolicy.DeclaredFile declaration = policy.validateDeclaration(
                "期末复习.md", "text/plain; charset=UTF-8", content.length);

        FileTypePolicy.InspectedFile inspected = policy.inspect(
                declaration, content.length, () -> new ByteArrayInputStream(content));

        assertThat(inspected.canonicalMime()).isEqualTo("text/markdown");
        assertThat(inspected.detectedMime()).isIn("text/plain", "text/x-web-markdown", "text/markdown");
    }

    @Test
    void rejectsLegacyOfficeArchivesAndPathLikeNames() {
        assertErrorCode(() -> policy.validateDeclaration("old.doc", null, 100), "UNSUPPORTED_FILE_TYPE");
        assertErrorCode(() -> policy.validateDeclaration("archive.zip", null, 100), "UNSUPPORTED_FILE_TYPE");
        assertErrorCode(() -> policy.validateDeclaration("../notes.txt", "text/plain", 100), "INVALID_FILENAME");
    }

    @Test
    void appliesTheSmallerImageLimitBeforeReadingContent() {
        assertErrorCode(
                () -> policy.validateDeclaration("large.png", "image/png", 20L * 1024 * 1024 + 1),
                "FILE_TOO_LARGE");
    }

    @Test
    void rejectsContentThatDoesNotMatchImageExtension() {
        byte[] content = "not an image".getBytes(StandardCharsets.UTF_8);
        FileTypePolicy.DeclaredFile declaration = policy.validateDeclaration(
                "fake.png", "image/png", content.length);

        assertThatThrownBy(() -> policy.inspect(
                declaration, content.length, () -> new ByteArrayInputStream(content)))
                .isInstanceOf(AssetApiException.class)
                .extracting(exception -> ((AssetApiException) exception).code())
                .isEqualTo("FILE_CONTENT_MISMATCH");
    }

    @Test
    void rejectsFilesThatOnlyRenameArbitraryBytesToDocx() {
        byte[] content = "not an office package".getBytes(StandardCharsets.UTF_8);
        FileTypePolicy.DeclaredFile declaration = policy.validateDeclaration(
                "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content.length);

        assertThatThrownBy(() -> policy.inspect(
                declaration, content.length, () -> new ByteArrayInputStream(content)))
                .isInstanceOf(AssetApiException.class)
                .extracting(exception -> ((AssetApiException) exception).code())
                .isEqualTo("INVALID_OFFICE_PACKAGE");
    }

    private void assertErrorCode(Runnable operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(AssetApiException.class)
                .extracting(exception -> ((AssetApiException) exception).code())
                .isEqualTo(expectedCode);
    }
}
