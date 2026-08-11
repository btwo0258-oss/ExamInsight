package com.example.llm.asset.processing.job;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AssetJobRunnerTest {

    @Test
    void workerIdIsAsciiAndIndependentFromTheComputerName() {
        String workerId = AssetJobRunner.newWorkerId();

        assertThat(workerId).startsWith("asset-worker-");
        assertThat(workerId).hasSizeLessThanOrEqualTo(128);
        assertThat(StandardCharsets.US_ASCII.newEncoder().canEncode(workerId)).isTrue();
    }
}
