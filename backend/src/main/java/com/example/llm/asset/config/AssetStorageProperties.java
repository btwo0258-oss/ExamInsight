package com.example.llm.asset.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

@Validated
@Getter
@Setter
@ConfigurationProperties("app.v2.storage")
public class AssetStorageProperties {
    public static final int REQUIRED_PART_SIZE = 8 * 1024 * 1024;

    @NotBlank
    @Pattern(regexp = "local|oss")
    private String mode = "local";

    @NotBlank
    @Size(max = 96)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String bucketKey = "local-private";

    @NotBlank
    @Size(min = 32)
    private String keySecret;

    @Min(1)
    private int partSize = REQUIRED_PART_SIZE;

    @NotNull
    private Duration uploadSessionTtl = Duration.ofHours(24);

    @Min(1)
    private int maxConcurrentUploads = 3;

    @Valid
    @NotNull
    private Local local = new Local();

    @AssertTrue(message = "V2 upload part size must remain 8 MiB to match the frozen database contract")
    public boolean isSupportedPartSize() {
        return partSize == REQUIRED_PART_SIZE;
    }

    @AssertTrue(message = "V2 upload session TTL must be positive")
    public boolean isSupportedSessionTtl() {
        return uploadSessionTtl != null && !uploadSessionTtl.isZero() && !uploadSessionTtl.isNegative();
    }

    @Getter
    @Setter
    public static class Local {
        @NotNull
        private Path root = Path.of("./data/v2-storage");
    }
}
