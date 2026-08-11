package com.example.llm.asset.processing.parse;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredTextChunkerTest {

    @Test
    void createsDeterministicBoundedChunksForMixedStudyNotes() {
        AssetProcessingProperties properties = new AssetProcessingProperties();
        properties.getParser().setTargetTokens(120);
        properties.getParser().setOverlapTokens(20);
        properties.getParser().setMaxTokens(180);
        StructuredTextChunker chunker = new StructuredTextChunker(properties);
        String text = ("高等数学极限与连续。The limit of a function describes local behavior.\n\n")
                .repeat(80);

        List<StructuredTextChunker.TextChunk> first = chunker.split(text);
        List<StructuredTextChunker.TextChunk> second = chunker.split(text);

        assertThat(first).isEqualTo(second).hasSizeGreaterThan(2);
        assertThat(first).allSatisfy(chunk -> {
            assertThat(chunk.tokenCount()).isBetween(1, 180);
            assertThat(chunk.content().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(65_536);
        });
        assertThat(first).extracting(StructuredTextChunker.TextChunk::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, first.size()).boxed().toList());
    }

    @Test
    void longLatinSequenceStillRespectsTokenAndByteLimits() {
        AssetProcessingProperties properties = new AssetProcessingProperties();
        StructuredTextChunker chunker = new StructuredTextChunker(properties);

        List<StructuredTextChunker.TextChunk> chunks = chunker.split("a".repeat(100_000));

        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(1800);
            assertThat(chunk.content().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(65_536);
        });
    }
}
