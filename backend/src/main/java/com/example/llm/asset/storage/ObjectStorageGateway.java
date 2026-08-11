package com.example.llm.asset.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorageGateway {
    void putPart(String uploadExternalId, int partNumber, long expectedPartSize, InputStream content)
            throws IOException;

    long uploadedBytes(String uploadExternalId) throws IOException;

    StoredObject complete(String uploadExternalId, long expectedSize, int expectedPartCount)
            throws IOException;

    InputStream open(String objectKey) throws IOException;

    void abortUpload(String uploadExternalId) throws IOException;

    void deleteObject(String objectKey) throws IOException;

    record StoredObject(
            String bucketKey,
            String objectKey,
            long sizeBytes,
            String sha256) {
    }
}
