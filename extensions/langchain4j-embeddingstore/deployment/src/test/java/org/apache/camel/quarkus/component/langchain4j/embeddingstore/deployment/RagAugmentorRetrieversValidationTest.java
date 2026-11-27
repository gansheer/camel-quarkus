/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.langchain4j.embeddingstore.deployment;

import java.util.List;
import java.util.Set;

import io.quarkus.runtime.configuration.ConfigurationException;
import org.apache.camel.quarkus.component.langchain4j.embeddingstore.deployment.Langchain4jEmbeddingstoreRagProcessor.AugmentorDefinition;
import org.junit.jupiter.api.Test;

import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.deployment.Langchain4jEmbeddingstoreRagProcessor.validateAugmentor;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAugmentorRetrieversValidationTest {

    @Test
    void storeOnlyIsValid() {
        assertDoesNotThrow(() -> validateAugmentor("a", new AugmentorDefinition("store", null, false), Set.of()));
    }

    @Test
    void retrieversOnlyIsValid() {
        assertDoesNotThrow(() -> validateAugmentor("a",
                new AugmentorDefinition(null, null, false, List.of("orders")), Set.of("orders", "kb")));
    }

    @Test
    void noSourceFailsTheBuild() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> validateAugmentor("a", new AugmentorDefinition(null, null, false), Set.of()));
        assertTrue(e.getMessage().contains("has no source"), e.getMessage());
    }

    @Test
    void unknownRetrieverFailsTheBuild() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> validateAugmentor("a", new AugmentorDefinition("store", null, false, List.of("orders", "typo")),
                        Set.of("orders")));
        assertTrue(e.getMessage().contains("[typo]"), e.getMessage());
        assertTrue(e.getMessage().contains("declared: [orders]"), e.getMessage());
    }
}
