package com.example.llm.chatv2.artifact;

import com.example.llm.asset.storage.ObjectStorageGateway;
import com.example.llm.asset.storage.ObjectStorageGateway.StoredObject;
import com.example.llm.asset.storage.StorageObjectKeyCipher;
import com.example.llm.auth.security.AuthCrypto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

@Service
public class GeneratedAssetWriter {
    private final ObjectStorageGateway storage;
    private final StorageObjectKeyCipher objectKeyCipher;
    private final AuthCrypto crypto;
    private final GeneratedAssetRepository repository;
    private final TransactionTemplate transactions;

    public GeneratedAssetWriter(
            ObjectStorageGateway storage,
            StorageObjectKeyCipher objectKeyCipher,
            AuthCrypto crypto,
            GeneratedAssetRepository repository,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions) {
        this.storage = storage;
        this.objectKeyCipher = objectKeyCipher;
        this.crypto = crypto;
        this.repository = repository;
        this.transactions = transactions;
    }

    public WrittenAsset write(
            long userId,
            Long aiRunId,
            String name,
            String generationLabel,
            String mimeType,
            byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Generated file is empty");
        }
        String uploadExternalId = crypto.newExternalId();
        StoredObject stored = null;
        try {
            storage.putPart(uploadExternalId, 1, bytes.length, new ByteArrayInputStream(bytes));
            stored = storage.complete(uploadExternalId, bytes.length, 1);
            StoredObject completed = stored;
            return Objects.requireNonNull(transactions.execute(status -> persistMetadata(
                    userId, aiRunId, name, generationLabel, mimeType, completed)));
        } catch (IOException exception) {
            abortQuietly(uploadExternalId);
            throw new IllegalStateException("Unable to persist generated file", exception);
        } catch (RuntimeException exception) {
            if (stored != null) {
                deleteQuietly(stored.objectKey());
            } else {
                abortQuietly(uploadExternalId);
            }
            throw exception;
        }
    }

    private WrittenAsset persistMetadata(
            long userId,
            Long aiRunId,
            String name,
            String generationLabel,
            String mimeType,
            StoredObject stored) {
        String storageExternalId = crypto.newExternalId();
        long storageId = repository.insertAvailableStorageObject(
                storageExternalId,
                userId,
                stored.bucketKey(),
                objectKeyCipher.encrypt(stored.objectKey()),
                crypto.digest("storage-object-key", stored.objectKey()),
                stored.sha256(),
                stored.sizeBytes(),
                mimeType);
        String assetExternalId = crypto.newExternalId();
        long assetId = repository.insertGeneratedAsset(assetExternalId, userId, safeName(name));
        var version = repository.insertGeneratedVersion(
                crypto.newExternalId(), assetId, storageId, stored.sha256(), mimeType,
                stored.sizeBytes(), aiRunId, generationLabel, userId);
        return new WrittenAsset(assetExternalId, version.id(), version.externalId());
    }

    private void abortQuietly(String uploadExternalId) {
        try {
            storage.abortUpload(uploadExternalId);
        } catch (IOException ignored) {
            // The periodic orphan cleanup job owns the final recovery path.
        }
    }

    private void deleteQuietly(String objectKey) {
        try {
            storage.deleteObject(objectKey);
        } catch (IOException ignored) {
            // The periodic orphan cleanup job owns the final recovery path.
        }
    }

    private String safeName(String value) {
        String normalized = value == null ? "AI generated content" : value.trim();
        return normalized.isEmpty()
                ? "AI generated content"
                : normalized.substring(0, Math.min(255, normalized.length()));
    }

    public record WrittenAsset(String assetExternalId, long versionId, String versionExternalId) {
    }
}
