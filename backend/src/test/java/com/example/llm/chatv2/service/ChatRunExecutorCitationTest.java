package com.example.llm.chatv2.service;

import com.example.llm.asset.retrieval.RetrievalModels.Mode;
import com.example.llm.asset.retrieval.RetrievalModels.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRunExecutorCitationTest {
    @Test
    void persistsOnlyRetrievedSourcesActuallyReferencedByTheAnswer() {
        Source first = source(1, "chunk-1", 3, "第一章", "第一条证据");
        Source second = source(2, "chunk-2", null, "第二章", "第二条证据");

        var citations = ChatRunExecutor.citedSources(
                "结论来自第一条证据 [S1]，重复标注 [s1] 不应重复；[S99] 不是真实来源。",
                List.of(first, second));

        assertThat(citations).singleElement().satisfies(citation -> {
            assertThat(citation.number()).isEqualTo(1);
            assertThat(citation.chunkExternalId()).isEqualTo("chunk-1");
            assertThat(citation.quotedText()).isEqualTo("第一条证据");
            assertThat(citation.locator()).isEqualTo("第 3 页");
        });
    }

    private Source source(
            int number,
            String chunkId,
            Integer page,
            String heading,
            String content) {
        return new Source(
                number, "S" + number, "asset-" + number, "资料-" + number,
                "version-" + number, chunkId, number, page, page, heading,
                page == null ? "{}" : "{\"page\":" + page + "}",
                content, 20, 0.8, Mode.HYBRID);
    }
}
