package com.example.llm.asset.storage;

import com.example.llm.asset.config.AssetStorageProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Component
public class StorageObjectKeyCipher {
    private static final byte FORMAT_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte[] KEY_DOMAIN = "ExamInsight:v2:storage-object-key".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public StorageObjectKeyCipher(AssetStorageProperties properties) {
        String secret = properties.getKeySecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("V2_STORAGE_KEY_SECRET must contain at least 32 characters");
        }
        this.key = new SecretKeySpec(deriveKey(secret), "AES");
    }

    public byte[] encrypt(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key must not be blank");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(KEY_DOMAIN);
            byte[] ciphertext = cipher.doFinal(objectKey.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
                    .put(FORMAT_VERSION)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt private object key", exception);
        }
    }

    public String decrypt(byte[] encoded) {
        if (encoded == null || encoded.length <= 1 + IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted object key");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            if (buffer.get() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported encrypted object key version");
            }
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(KEY_DOMAIN);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt private object key", exception);
        }
    }

    private byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_DOMAIN);
            digest.update((byte) 0);
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive private storage key", exception);
        }
    }
}
