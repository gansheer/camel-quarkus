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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;

/**
 * The retrievers declared in configuration and with {@link Retriever @Retriever}, created once and registered with
 * the Camel context, which starts and stops them.
 */
public class CamelRetrievers {

    public record BuilderEntry(String name, String className, String methodName) {
    }

    private static final String CONFIG_PREFIX = "quarkus.camel.langchain4j.retriever.";
    // the properties of RetrieverRunTimeConfig.RetrieverConfig; uri is build time, where a clash already fails the build
    private static final List<String> RUNTIME_PROPERTIES = List.of("timeout", "on-error", "max-results", "max-chars",
            "concurrency", "queue-capacity", "document-id-key");

    private final Set<String> configNames;
    private final Map<String, BuilderEntry> builders = new HashMap<>();
    private final Map<String, CamelContentRetriever> retrievers = new HashMap<>();
    private CamelContext camelContext;

    public CamelRetrievers(Set<String> configNames, List<BuilderEntry> builders) {
        this.configNames = configNames;
        builders.forEach(entry -> this.builders.put(entry.name(), entry));
    }

    public boolean contains(String name) {
        return configNames.contains(name) || builders.containsKey(name);
    }

    /** Creates every retriever, so that a wrong endpoint fails the Camel context start rather than the first call. */
    public synchronized void createAll(CamelContext camelContext) {
        this.camelContext = camelContext;
        configNames.forEach(this::retriever);
        builders.keySet().forEach(this::retriever);
    }

    public synchronized CamelContentRetriever retriever(String name) {
        CamelContentRetriever retriever = retrievers.get(name);
        if (retriever != null) {
            return retriever;
        }
        if (camelContext == null) {
            throw new IllegalStateException("Camel retriever '" + name + "' requested before the Camel context exists");
        }
        ArcContainer container = Arc.container();
        if (configNames.contains(name)) {
            String uri = container.instance(RetrieverBuildTimeConfig.class).get().retrievers().get(name).uri();
            retriever = new CamelContentRetriever(camelContext, name, uri);
            configure(retriever, container.instance(RetrieverRunTimeConfig.class).get().retrievers().get(name));
        } else if (builders.containsKey(name)) {
            rejectConfiguration(name);
            RetrieverSpec spec = invoke(builders.get(name));
            retriever = new CamelContentRetriever(camelContext, name, spec.uri());
            spec.configure(retriever);
        } else {
            throw new IllegalArgumentException("No Camel retriever named '" + name + "'");
        }
        try {
            // started with the context, or now when it already runs
            camelContext.addService(retriever);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        retrievers.put(name, retriever);
        return retriever;
    }

    private static void configure(CamelContentRetriever retriever, RetrieverRunTimeConfig.RetrieverConfig config) {
        retriever.setDocumentIdKey(config.documentIdKey().orElse(null))
                .setTimeout(config.timeout())
                .setOnError(config.onError())
                .setMaxResults(config.maxResults())
                .setMaxChars(config.maxChars())
                .setConcurrency(config.concurrency())
                .setQueueCapacity(config.queueCapacity());
    }

    /**
     * Every setting of a {@code @Retriever} retriever comes from its {@link RetrieverSpec}: configuration for it would be
     * quietly overruled, so it fails the startup instead, as for an {@code @Ingest} pipeline's source. Values from the
     * mapping defaults (lowest ordinal) do not count; environment variables do.
     */
    private static void rejectConfiguration(String name) {
        Config config = ConfigProvider.getConfig();
        // a name with a dot is quoted in a property name
        String prefix = CONFIG_PREFIX + (name.contains(".") ? "\"" + name + "\"" : name) + ".";
        List<String> keys = new ArrayList<>();
        for (String property : RUNTIME_PROPERTIES) {
            ConfigValue value = config.getConfigValue(prefix + property);
            if (value.getValue() != null && value.getSourceOrdinal() > Integer.MIN_VALUE) {
                keys.add(prefix + property);
            }
        }
        if (!keys.isEmpty()) {
            throw new IllegalStateException("Camel retriever '" + name + "' is declared with @Retriever, so its "
                    + "settings come from its RetrieverSpec. Remove " + keys + ", or declare the retriever in "
                    + "configuration instead.");
        }
    }

    // the @Consume pattern, as for @Ingest: validated at build time, registered for reflection, run once at startup
    private static RetrieverSpec invoke(BuilderEntry entry) {
        try {
            Class<?> declaringClass = Thread.currentThread().getContextClassLoader().loadClass(entry.className());
            Instance.Handle<?> bean = CDI.current().select(declaringClass).getHandle();
            try {
                Method method = declaringClass.getDeclaredMethod(entry.methodName());
                method.setAccessible(true);
                RetrieverSpec spec = (RetrieverSpec) method.invoke(bean.get());
                if (spec == null) {
                    throw new IllegalStateException("@Retriever method " + entry.className() + "#" + entry.methodName()
                            + " returned null for retriever '" + entry.name() + "'");
                }
                return spec;
            } finally {
                // a @Dependent declaring bean exists only for this call and would otherwise never be destroyed
                if (Dependent.class.equals(bean.getBean().getScope())) {
                    bean.destroy();
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke @Retriever method " + entry.className() + "#"
                    + entry.methodName() + " for retriever '" + entry.name() + "'", e);
        }
    }
}
