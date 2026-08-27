package com.example.llm.asset.processing.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.retrieval.ElasticsearchVectorSearchGateway;
import com.example.llm.asset.retrieval.VectorSearchGateway;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ElasticsearchVectorIndexGatewayIntegrationTest {
    @Container
    static final GenericContainer<?> ELASTICSEARCH = new GenericContainer<>(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.19.19"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("action.auto_create_index", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static RestClient restClient;
    private static ElasticsearchClient client;

    @BeforeAll
    static void connect() {
        restClient = RestClient.builder(new HttpHost(
                ELASTICSEARCH.getHost(), ELASTICSEARCH.getMappedPort(9200), "http")).build();
        client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @AfterAll
    static void closeClient() throws Exception {
        if (restClient != null) {
            restClient.close();
        }
    }

    @Test
    void createsStrict1024DimensionIndexAndIdempotentlyOverwritesDocument() throws Exception {
        AssetProcessingProperties properties = new AssetProcessingProperties();
        properties.getIndexing().setIndexName("examinsight-v2-vector-integration-test");
        ElasticsearchVectorIndexGateway gateway = new ElasticsearchVectorIndexGateway(client, properties);
        VectorIndexGateway.VectorDocument document = new VectorIndexGateway.VectorDocument(
                "embedding-record-1", "01J00000000000000000000100",
                7L, 11L, 13L, 17L, 1,
                "a".repeat(64), properties.getIndexing().getEmbeddingVersion(),
                Collections.nCopies(1024, 0.01f));

        gateway.ensureIndex();
        gateway.upsert(document);
        gateway.upsert(document);
        client.indices().refresh(request -> request.index(properties.getIndexing().getIndexName()));

        assertThat(client.count(request -> request.index(properties.getIndexing().getIndexName())).count())
                .isEqualTo(1);
        @SuppressWarnings("rawtypes")
        Map source = client.get(request -> request
                        .index(properties.getIndexing().getIndexName())
                        .id(document.documentId()), Map.class)
                .source();
        assertThat(source).containsEntry("chunk_external_id", document.chunkExternalId());
        assertThat(source).doesNotContainKey("embedding");
        Integer dimensions = client.indices()
                .getMapping(request -> request.index(properties.getIndexing().getIndexName()))
                .result().get(properties.getIndexing().getIndexName())
                .mappings().properties().get("embedding")
                .denseVector().dims();
        assertThat(dimensions).isEqualTo(1024);
    }

    @Test
    void vectorSearchAppliesOwnerAndAssetFiltersBeforeKnnSelection() throws Exception {
        AssetProcessingProperties properties = new AssetProcessingProperties();
        properties.getIndexing().setIndexName("examinsight-v2-vector-filter-test");
        ElasticsearchVectorIndexGateway index = new ElasticsearchVectorIndexGateway(client, properties);
        index.ensureIndex();
        index.upsert(document("record-owner-a", "chunk-owner-a", 7, 11, 13, unitVector(0)));
        index.upsert(document("record-owner-b", "chunk-owner-b", 7, 12, 14, unitVector(1)));
        index.upsert(document("record-foreign", "chunk-foreign", 8, 21, 23, unitVector(0)));
        client.indices().refresh(request -> request.index(properties.getIndexing().getIndexName()));

        ElasticsearchVectorSearchGateway search = new ElasticsearchVectorSearchGateway(client, properties);
        List<VectorSearchGateway.VectorHit> ownerHits = search.search(
                unitVector(0), 7, null, null, 10, 20, 0);
        assertThat(ownerHits)
                .extracting(VectorSearchGateway.VectorHit::chunkExternalId)
                .contains("chunk-owner-a", "chunk-owner-b")
                .doesNotContain("chunk-foreign");

        List<VectorSearchGateway.VectorHit> assetHits = search.search(
                unitVector(0), 7, List.of(12L), null, 10, 20, 0);
        assertThat(assetHits)
                .extracting(VectorSearchGateway.VectorHit::chunkExternalId)
                .containsExactly("chunk-owner-b");

        List<VectorSearchGateway.VectorHit> versionHits = search.search(
                unitVector(0), 7, null, List.of(13L), 10, 20, 0);
        assertThat(versionHits)
                .extracting(VectorSearchGateway.VectorHit::chunkExternalId)
                .containsExactly("chunk-owner-a");

        index.deleteByAsset(7, 11);
        client.indices().refresh(request -> request.index(properties.getIndexing().getIndexName()));
        List<VectorSearchGateway.VectorHit> remainingOwnerHits = search.search(
                unitVector(0), 7, null, null, 10, 20, 0);
        assertThat(remainingOwnerHits)
                .extracting(VectorSearchGateway.VectorHit::chunkExternalId)
                .containsExactly("chunk-owner-b");
        List<VectorSearchGateway.VectorHit> remainingForeignHits = search.search(
                unitVector(0), 8, null, null, 10, 20, 0);
        assertThat(remainingForeignHits)
                .extracting(VectorSearchGateway.VectorHit::chunkExternalId)
                .containsExactly("chunk-foreign");
    }

    private VectorIndexGateway.VectorDocument document(
            String documentId,
            String chunkId,
            long userId,
            long assetId,
            long versionId,
            List<Float> embedding) {
        return new VectorIndexGateway.VectorDocument(
                documentId, chunkId, userId, assetId, versionId, versionId + 100,
                1, "b".repeat(64), "dashscope-qwen3.7-text-embedding-1024-v1", embedding);
    }

    private List<Float> unitVector(int dimension) {
        List<Float> vector = new ArrayList<>(Collections.nCopies(1024, 0f));
        vector.set(dimension, 1f);
        return vector;
    }
}
