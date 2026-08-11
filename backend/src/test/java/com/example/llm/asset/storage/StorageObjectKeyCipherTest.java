package com.example.llm.asset.storage;

import com.example.llm.asset.config.AssetStorageProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StorageObjectKeyCipherTest {
    @Test
    void encryptsWithRandomIvAndDecryptsWithoutPersistingPlaintext() {
        AssetStorageProperties properties = new AssetStorageProperties();
        properties.setKeySecret("test-storage-secret-that-is-longer-than-thirty-two-characters");
        StorageObjectKeyCipher cipher = new StorageObjectKeyCipher(properties);
        String objectKey = "quarantine/01/01ARZ3NDEKTSV4RRFFQ69G5FAV.bin";

        byte[] first = cipher.encrypt(objectKey);
        byte[] second = cipher.encrypt(objectKey);

        assertThat(first).isNotEqualTo(second);
        assertThat(new String(first, StandardCharsets.UTF_8)).doesNotContain(objectKey);
        assertThat(cipher.decrypt(first)).isEqualTo(objectKey);
        assertThat(cipher.decrypt(second)).isEqualTo(objectKey);
    }
}
