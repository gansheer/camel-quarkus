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
package org.apache.camel.quarkus.component.langchain4j.content.retriever;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.apache.camel.CamelContext;

@Recorder
public class CamelRetrieverRecorder {

    /** @param flatBuilders triples: name, class name, method name */
    public RuntimeValue<CamelRetrievers> createRetrievers(List<String> configNames, List<String> flatBuilders) {
        List<CamelRetrievers.BuilderEntry> builders = new ArrayList<>(flatBuilders.size() / 3);
        for (int i = 0; i < flatBuilders.size(); i += 3) {
            builders.add(new CamelRetrievers.BuilderEntry(flatBuilders.get(i), flatBuilders.get(i + 1),
                    flatBuilders.get(i + 2)));
        }
        return new RuntimeValue<>(new CamelRetrievers(new HashSet<>(configNames), builders));
    }

    public Supplier<CamelContentRetriever> retrieverSupplier(String name) {
        return () -> Arc.container().instance(CamelRetrievers.class).get().retriever(name);
    }

    /** Recorded as a pre-start Camel runtime task: the retrievers then start with the context. */
    public void initRetrievers(RuntimeValue<CamelContext> camelContext) {
        Arc.container().instance(CamelRetrievers.class).get().createAll(camelContext.getValue());
    }
}
