package com.example.llm.asset.storage;

import com.example.llm.asset.config.AssetStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "app.v2.storage", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageGateway implements ObjectStorageGateway {
    private static final Pattern EXTERNAL_ID = Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");
    private static final Pattern OBJECT_KEY = Pattern.compile("quarantine/[0-9A-HJKMNP-TV-Z]{2}/[0-9A-HJKMNP-TV-Z]{26}\\.bin");
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path root;
    private final Path partsRoot;
    private final Path objectsRoot;
    private final String bucketKey;

    public LocalObjectStorageGateway(AssetStorageProperties properties) {
        this.root = properties.getLocal().getRoot().toAbsolutePath().normalize();
        this.partsRoot = root.resolve("parts").normalize();
        this.objectsRoot = root.resolve("objects").normalize();
        this.bucketKey = properties.getBucketKey();
        try {
            Files.createDirectories(partsRoot);
            Files.createDirectories(objectsRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize V2 local private storage", exception);
        }
    }

    @Override
    public void putPart(String uploadExternalId, int partNumber, long expectedPartSize, InputStream content)
            throws IOException {
        validateUploadId(uploadExternalId);
        if (partNumber < 1 || expectedPartSize < 1) {
            throw new IllegalArgumentException("Invalid upload part metadata");
        }

        Path directory = uploadPartsDirectory(uploadExternalId);
        Files.createDirectories(directory);
        Path target = partPath(directory, partNumber);
        Path temporary = safeResolve(directory, target.getFileName() + ".tmp-" + UUID.randomUUID());

        long written = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(
                temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            while (written <= expectedPartSize) {
                int maximum = (int) Math.min(buffer.length, expectedPartSize + 1 - written);
                int read = content.read(buffer, 0, maximum);
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                written += read;
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }

        if (written != expectedPartSize || content.read() != -1) {
            Files.deleteIfExists(temporary);
            throw new IOException("Upload part length does not match the expected length");
        }
        moveReplacing(temporary, target);
    }

    @Override
    public long uploadedBytes(String uploadExternalId) throws IOException {
        Path directory = uploadPartsDirectory(uploadExternalId);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var paths = Files.list(directory)) {
            return paths
                    .filter(path -> path.getFileName().toString().matches("part-\\d{6}"))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            throw new StorageReadException(exception);
                        }
                    })
                    .sum();
        } catch (StorageReadException exception) {
            throw exception.ioException;
        }
    }

    @Override
    public StoredObject complete(String uploadExternalId, long expectedSize, int expectedPartCount)
            throws IOException {
        validateUploadId(uploadExternalId);
        if (expectedSize < 1 || expectedPartCount < 1) {
            throw new IllegalArgumentException("Invalid upload completion metadata");
        }

        String objectKey = objectKey(uploadExternalId);
        Path target = objectPath(objectKey);
        if (Files.isRegularFile(target)) {
            return describeExisting(target, objectKey, expectedSize);
        }

        Path partsDirectory = uploadPartsDirectory(uploadExternalId);
        Files.createDirectories(target.getParent());
        Path temporary = safeResolve(target.getParent(), target.getFileName() + ".assembling-" + UUID.randomUUID());
        MessageDigest digest = sha256Digest();
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (OutputStream raw = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             OutputStream output = new BufferedOutputStream(raw)) {
            for (int partNumber = 1; partNumber <= expectedPartCount; partNumber++) {
                Path part = partPath(partsDirectory, partNumber);
                if (!Files.isRegularFile(part)) {
                    throw new IOException("Upload is incomplete: missing part " + partNumber);
                }
                try (InputStream input = new BufferedInputStream(Files.newInputStream(part))) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        output.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        total += read;
                        if (total > expectedSize) {
                            throw new IOException("Assembled upload exceeds expected size");
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }

        if (total != expectedSize) {
            Files.deleteIfExists(temporary);
            throw new IOException("Assembled upload size does not match expected size");
        }
        moveWithoutReplacing(temporary, target);
        deleteDirectory(partsDirectory);
        return new StoredObject(bucketKey, objectKey, total, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return new BufferedInputStream(Files.newInputStream(objectPath(objectKey), StandardOpenOption.READ));
    }

    @Override
    public void abortUpload(String uploadExternalId) throws IOException {
        deleteDirectory(uploadPartsDirectory(uploadExternalId));
    }

    @Override
    public void deleteObject(String objectKey) throws IOException {
        Files.deleteIfExists(objectPath(objectKey));
    }

    private StoredObject describeExisting(Path path, String objectKey, long expectedSize) throws IOException {
        long size = Files.size(path);
        if (size != expectedSize) {
            throw new IOException("Existing stored object size does not match upload session");
        }
        MessageDigest digest = sha256Digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return new StoredObject(bucketKey, objectKey, size, HexFormat.of().formatHex(digest.digest()));
    }

    private String objectKey(String uploadExternalId) {
        return "quarantine/" + uploadExternalId.substring(0, 2) + "/" + uploadExternalId + ".bin";
    }

    private Path uploadPartsDirectory(String uploadExternalId) {
        validateUploadId(uploadExternalId);
        return safeResolve(partsRoot, uploadExternalId);
    }

    private Path partPath(Path directory, int partNumber) {
        return safeResolve(directory, "part-%06d".formatted(partNumber));
    }

    private Path objectPath(String objectKey) {
        if (objectKey == null || !OBJECT_KEY.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("Invalid private object key");
        }
        return safeResolve(objectsRoot, objectKey);
    }

    private Path safeResolve(Path parent, String child) {
        Path resolved = parent.resolve(child).normalize();
        if (!resolved.startsWith(parent)) {
            throw new IllegalArgumentException("Storage path escapes its private root");
        }
        return resolved;
    }

    private void validateUploadId(String uploadExternalId) {
        if (uploadExternalId == null || !EXTERNAL_ID.matcher(uploadExternalId).matches()) {
            throw new IllegalArgumentException("Invalid upload identifier");
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!directory.startsWith(partsRoot) || directory.equals(partsRoot) || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void moveWithoutReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static final class StorageReadException extends RuntimeException {
        private final IOException ioException;

        private StorageReadException(IOException ioException) {
            this.ioException = ioException;
        }
    }
}
