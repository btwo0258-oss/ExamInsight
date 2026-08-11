package com.example.llm.asset;

import com.example.llm.asset.api.AssetApiException;
import com.example.llm.asset.repository.AssetLibraryRepository;
import com.example.llm.asset.repository.AssetLibraryRepository.AssetRow;
import com.example.llm.asset.repository.AssetLibraryRepository.ReadableAssetContent;
import com.example.llm.asset.repository.AssetLibraryRepository.VersionRow;
import com.example.llm.asset.service.AssetContentService;
import com.example.llm.asset.storage.ObjectStorageGateway;
import com.example.llm.asset.storage.StorageObjectKeyCipher;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetContentServiceTest {
    private final AssetLibraryRepository assets = mock(AssetLibraryRepository.class);
    private final ObjectStorageGateway storage = mock(ObjectStorageGateway.class);
    private final StorageObjectKeyCipher cipher = mock(StorageObjectKeyCipher.class);
    private final AssetContentService service = new AssetContentService(assets, storage, cipher);

    @Test
    void opensOnlyTheOwnedReadableVersion() throws Exception {
        byte[] encryptedKey = {1, 2, 3};
        when(assets.findReadableContent(7, "ASSET01")).thenReturn(Optional.of(
                new ReadableAssetContent("notes.txt", "text/plain", 5, encryptedKey)));
        when(cipher.decrypt(encryptedKey)).thenReturn("quarantine/ASSET01.bin");
        when(storage.open("quarantine/ASSET01.bin"))
                .thenReturn(new ByteArrayInputStream("hello".getBytes()));

        var content = service.open(7, "ASSET01");

        assertThat(content.name()).isEqualTo("notes.txt");
        assertThat(content.stream().readAllBytes()).isEqualTo("hello".getBytes());
        verify(assets).findReadableContent(7, "ASSET01");
    }

    @Test
    void doesNotRevealAnAssetOwnedByAnotherUser() {
        when(assets.findReadableContent(8, "ASSET01")).thenReturn(Optional.empty());
        when(assets.findByExternalId(8, "ASSET01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(8, "ASSET01"))
                .isInstanceOfSatisfying(AssetApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("ASSET_NOT_FOUND"));
    }

    @Test
    void blocksContentUntilSecurityAndParsingAreComplete() {
        VersionRow version = new VersionRow(
                "VERSION01", 1, "PROCESSING", "text/plain", 5,
                0, 0, 0, LocalDateTime.now());
        AssetRow asset = new AssetRow(
                1, "ASSET01", "notes.txt", "DOCUMENT", "UPLOAD", "ACTIVE",
                0, version, null, LocalDateTime.now(), LocalDateTime.now());
        when(assets.findReadableContent(7, "ASSET01")).thenReturn(Optional.empty());
        when(assets.findByExternalId(7, "ASSET01")).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.open(7, "ASSET01"))
                .isInstanceOfSatisfying(AssetApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("ASSET_NOT_READY"));
    }
}
