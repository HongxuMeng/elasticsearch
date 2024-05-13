/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.qa.mixed;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.Version;
import org.elasticsearch.common.Strings;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.test.http.MockResponse;
import org.elasticsearch.test.http.MockWebServer;
import org.elasticsearch.xpack.inference.services.cohere.CohereService;
import org.elasticsearch.xpack.inference.services.huggingface.HuggingFaceService;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

public class HuggingFaceServiceMixedIT extends BaseMixedTestCase {

    private static final String HF_EMBEDDINGS_ADDED = "8.12.0";
    private static final String HF_ELSER_ADDED = "8.12.0";
    private static final String MINIMUM_SUPPORTED_VERSION = "8.15.0";

    private static MockWebServer embeddingsServer;
    private static MockWebServer elserServer;

    @BeforeClass
    public static void startWebServer() throws IOException {
        embeddingsServer = new MockWebServer();
        embeddingsServer.start();

        elserServer = new MockWebServer();
        elserServer.start();
    }

    @AfterClass
    public static void shutdown() {
        embeddingsServer.close();
        elserServer.close();
    }

    @SuppressWarnings("unchecked")
    public void testHFEmbeddings() throws IOException {
        var embeddingsSupported = bwcVersion.onOrAfter(Version.fromString(HF_EMBEDDINGS_ADDED));
        assumeTrue("Hugging Face embedding service added in " + HF_EMBEDDINGS_ADDED, embeddingsSupported);
        assumeTrue(
            "HuggingFace service requires at least " + MINIMUM_SUPPORTED_VERSION,
            bwcVersion.onOrAfter(Version.fromString(MINIMUM_SUPPORTED_VERSION))
        );

        final String inferenceId = "mixed-cluster-embeddings";

        embeddingsServer.enqueue(new MockResponse().setResponseCode(200).setBody(embeddingResponse()));
        put(inferenceId, embeddingConfig(getUrl(embeddingsServer)), TaskType.TEXT_EMBEDDING);
        var configs = (List<Map<String, Object>>) get(TaskType.TEXT_EMBEDDING, inferenceId).get("endpoints");
        assertThat(configs, hasSize(1));
        assertEquals("hugging_face", configs.get(0).get("service"));
        assertEmbeddingInference(inferenceId);
    }

    void assertEmbeddingInference(String inferenceId) throws IOException {
        embeddingsServer.enqueue(new MockResponse().setResponseCode(200).setBody(embeddingResponse()));
        var inferenceMap = inference(inferenceId, TaskType.TEXT_EMBEDDING, "some text");
        assertThat(inferenceMap.entrySet(), not(empty()));
    }

    @SuppressWarnings("unchecked")
    public void testElser() throws IOException {
        var supported = bwcVersion.onOrAfter(Version.fromString(HF_ELSER_ADDED));
        assumeTrue("HF elser service added in " + HF_ELSER_ADDED, supported);
        assumeTrue(
            "HuggingFace service requires at least " + MINIMUM_SUPPORTED_VERSION,
            bwcVersion.onOrAfter(Version.fromString(MINIMUM_SUPPORTED_VERSION))
        );

        final String inferenceId = "mixed-cluster-elser";
        final String upgradedClusterId = "upgraded-cluster-elser";

        put(inferenceId, elserConfig(getUrl(elserServer)), TaskType.SPARSE_EMBEDDING);

        var configs = (List<Map<String, Object>>) get(TaskType.SPARSE_EMBEDDING, inferenceId).get("endpoints");
        assertThat(configs, hasSize(1));
        assertEquals("hugging_face", configs.get(0).get("service"));
        assertElser(inferenceId);
    }

    private void assertElser(String inferenceId) throws IOException {
        elserServer.enqueue(new MockResponse().setResponseCode(200).setBody(elserResponse()));
        var inferenceMap = inference(inferenceId, TaskType.SPARSE_EMBEDDING, "some text");
        assertThat(inferenceMap.entrySet(), not(empty()));
    }

    private String embeddingConfig(String url) {
        return Strings.format("""
            {
                "service": "hugging_face",
                "service_settings": {
                    "url": "%s",
                    "api_key": "XXXX"
                }
            }
            """, url);
    }

    private String embeddingResponse() {
        return """
            [
                  [
                      0.014539449,
                      -0.015288644
                  ]
            ]
            """;
    }

    private String elserConfig(String url) {
        return Strings.format("""
            {
                "service": "hugging_face",
                "service_settings": {
                    "api_key": "XXXX",
                    "url": "%s"
                }
            }
            """, url);
    }

    private String elserResponse() {
        return """
            [
                {
                    ".": 0.133155956864357,
                    "the": 0.6747211217880249
                }
            ]
            """;
    }

}
