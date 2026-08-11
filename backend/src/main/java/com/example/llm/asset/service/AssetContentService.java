package com.example.llm.asset.service;

import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.repository.AssetLibraryRepository;
import com.example.llm.asset.repository.AssetLibraryRepository.ReadableAssetContent;
import com.example.llm.asset.storage.ObjectStorageGateway;
import com.example.llm.asset.storage.StorageObjectKeyCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Slf4j
@Service
public class AssetContentService {
    private final AssetLibraryRepository assets;
    private final ObjectStorageGateway storage;
    private final StorageObjectKeyCipher objectKeyCipher;

    public AssetContentService(
            AssetLibraryRepository assets,
            ObjectStorageGateway storage,
            StorageObjectKeyCipher objectKeyCipher) {
        this.assets = assets;
        this.storage = storage;
        this.objectKeyCipher = objectKeyCipher;
    }

    public OpenedAssetContent open(long userId, String assetExternalId) {
        ReadableAssetContent content = assets.findReadableContent(userId, assetExternalId)
                .orElseGet(() -> unavailableContent(userId, assetExternalId));
        try {
            String objectKey = objectKeyCipher.decrypt(content.encryptedObjectKey());
            return new OpenedAssetContent(
                    content.name(), content.mimeType(), content.sizeBytes(), storage.open(objectKey));
        } catch (IOException exception) {
            log.error("Unable to open V2 asset content, assetId={}", assetExternalId, exception);
            throw new AssetApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_UNAVAILABLE",
                    "文件存储暂时不可用，请稍后重试。",
                    Map.of());
        }
    }

    private ReadableAssetContent unavailableContent(long userId, String assetExternalId) {
        var asset = assets.findByExternalId(userId, assetExternalId)
                .orElseThrow(() -> new AssetApiException(
                        HttpStatus.NOT_FOUND,
                        "ASSET_NOT_FOUND",
                        "资料不存在或已被删除。",
                        Map.of()));
        if (!asset.status().equals("ACTIVE")) {
            throw new AssetApiException(
                    HttpStatus.CONFLICT,
                    "ASSET_NOT_ACTIVE",
                    "回收站中的资料不能预览或下载，请先恢复。",
                    Map.of("status", asset.status()));
        }
        throw new AssetApiException(
                HttpStatus.CONFLICT,
                "ASSET_NOT_READY",
                "资料仍在安全检查或解析中，请稍后再试。",
                Map.of("versionStatus", asset.version() == null ? "MISSING" : asset.version().status()));
    }

    public record OpenedAssetContent(
            String name,
            String mimeType,
            long sizeBytes,
            InputStream stream) {
    }
}
