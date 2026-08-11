package com.example.llm.asset.storage;

import com.example.llm.asset.config.AssetStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalObjectStorageGatewayTest {
    private static final String UPLOAD_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @TempDir
    Path temporaryDirectory;

    @Test
    void overwritesPartsIdempotentlyAndAssemblesPrivateObject() throws Exception {
        LocalObjectStorageGateway storage = storage();

        storage.putPart(UPLOAD_ID, 1, 3, bytes("old"));
        storage.putPart(UPLOAD_ID, 1, 3, bytes("abc"));
        storage.putPart(UPLOAD_ID, 2, 2, bytes("de"));

        assertThat(storage.uploadedBytes(UPLOAD_ID)).isEqualTo(5);
        ObjectStorageGateway.StoredObject object = storage.complete(UPLOAD_ID, 5, 2);

        assertThat(object.bucketKey()).isEqualTo("test-private");
        assertThat(object.objectKey()).isEqualTo("quarantine/01/" + UPLOAD_ID + ".bin");
        assertThat(object.sha256()).isEqualTo(sha256("abcde"));
        try (var input = storage.open(object.objectKey())) {
            assertThat(input.readAllBytes()).isEqualTo("abcde".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(storage.uploadedBytes(UPLOAD_ID)).isZero();

        assertThat(storage.complete(UPLOAD_ID, 5, 2)).isEqualTo(object);
    }

    @Test
    void rejectsWrongPartLengthWithoutReplacingValidPart() throws Exception {
        LocalObjectStorageGateway storage = storage();
        storage.putPart(UPLOAD_ID, 1, 3, bytes("abc"));

        assertThatThrownBy(() -> storage.putPart(UPLOAD_ID, 1, 3, bytes("no")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("length");
        assertThat(storage.uploadedBytes(UPLOAD_ID)).isEqualTo(3);
    }

    @Test
    void abortOnlyRemovesTheSelectedUploadDirectory() throws Exception {
        LocalObjectStorageGateway storage = storage();
        storage.putPart(UPLOAD_ID, 1, 3, bytes("abc"));
        Path unrelated = temporaryDirectory.resolve("keep.txt");
        Files.writeString(unrelated, "keep");

        storage.abortUpload(UPLOAD_ID);

        assertThat(storage.uploadedBytes(UPLOAD_ID)).isZero();
        assertThat(unrelated).exists();
    }

    private LocalObjectStorageGateway storage() {
        AssetStorageProperties properties = new AssetStorageProperties();
        properties.setBucketKey("test-private");
        properties.getLocal().setRoot(temporaryDirectory.resolve("storage"));
        return new LocalObjectStorageGateway(properties);
    }

    private ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
