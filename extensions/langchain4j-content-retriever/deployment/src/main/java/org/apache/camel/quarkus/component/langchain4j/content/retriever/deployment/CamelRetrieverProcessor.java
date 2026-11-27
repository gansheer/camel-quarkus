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
package org.apache.camel.quarkus.component.langchain4j.content.retriever.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.QualifierRegistrarBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.CamelContentRetriever;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.CamelRetrieverName;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.CamelRetrieverRecorder;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.CamelRetrievers;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.Retriever;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.RetrieverBuildTimeConfig;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.RetrieverSpec;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeTaskBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

/**
 * Camel content retrievers declared in configuration ({@code quarkus.camel.langchain4j.retriever.<name>}) or with
 * {@code @Retriever}: one {@code @Named} {@link ContentRetriever} bean each, registered with the Camel context. They
 * do not need Quarkus LangChain4j: any LangChain4j augmentor can use them.
 */
class CamelRetrieverProcessor {

    // the name is a bean name, a log label and the camel_retrieval_source of every item
    static final String NAME_PATTERN = "[A-Za-z0-9._-]+";

    // CamelRetrieverName lives in the extension runtime artifact, which ships no Jandex index
    @BuildStep
    QualifierRegistrarBuildItem registerCamelRetrieverNameQualifier() {
        return new QualifierRegistrarBuildItem(
                () -> Map.of(DotName.createSimple(CamelRetrieverName.class.getName()), Set.of()));
    }

    /**
     * Validates the configured names and the {@code @Retriever} methods (name charset, return type, no parameters,
     * unique names), reporting every violation as a {@link ValidationErrorBuildItem}, as the ingest extension does.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void discoverRetrievers(
            CombinedIndexBuildItem combinedIndex,
            RetrieverBuildTimeConfig config,
            CamelRetrieverRecorder recorder,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<CamelRetrieverNamesBuildItem> retrieverNames,
            BuildProducer<ValidationErrorBuildItem> validationErrors) {

        Set<String> names = new TreeSet<>();
        List<String> configNames = new ArrayList<>();
        for (String name : config.retrievers().keySet()) {
            if (!name.matches(NAME_PATTERN)) {
                validationErrors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                        "Camel retriever name '" + name + "' may only contain letters, digits, '.', '_' and '-'")));
                continue;
            }
            names.add(name);
            configNames.add(name);
        }

        DotName retrieverAnnotation = DotName.createSimple(Retriever.class.getName());
        DotName specType = DotName.createSimple(RetrieverSpec.class.getName());
        List<String> flatBuilders = new ArrayList<>();
        Set<String> beanClasses = new TreeSet<>();

        for (AnnotationInstance annotation : combinedIndex.getIndex().getAnnotations(retrieverAnnotation)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo method = annotation.target().asMethod();
            String name = annotation.value().asString();
            String location = method.declaringClass().name() + "#" + method.name();

            if (!name.matches(NAME_PATTERN)) {
                validationErrors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                        "@Retriever on " + location + " has name '" + name
                                + "', which may only contain letters, digits, '.', '_' and '-'")));
                continue;
            }
            if (!method.returnType().name().equals(specType)) {
                validationErrors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                        "@Retriever method " + location + " must return " + RetrieverSpec.class.getSimpleName())));
                continue;
            }
            if (!method.parameters().isEmpty()) {
                validationErrors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                        "@Retriever method " + location + " must take no parameters")));
                continue;
            }
            // invoked on a CDI bean instance: a static method bypasses it, a private or final one would run against
            // the client proxy and see null injected fields
            if (Modifier.isPrivate(method.flags()) || Modifier.isStatic(method.flags())
                    || Modifier.isFinal(method.flags()) || Modifier.isFinal(method.declaringClass().flags())) {
                validationErrors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                        "@Retriever method " + location
                                + " must not be private, static or final, nor declared on a final class")));
                continue;
            }
            if (!names.add(name)) {
                validationErrors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                        "Camel retriever '" + name + "' is declared more than once (@Retriever and/or "
                                + "configuration). Retriever names must be unique.")));
                continue;
            }
            flatBuilders.add(name);
            flatBuilders.add(method.declaringClass().name().toString());
            flatBuilders.add(method.name());
            beanClasses.add(method.declaringClass().name().toString());
        }

        if (!beanClasses.isEmpty()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(beanClasses.toArray(new String[0]))
                    .setUnremovable()
                    .build());
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(beanClasses.toArray(new String[0]))
                    .methods()
                    .build());
        }

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(CamelRetrievers.class)
                .scope(Singleton.class)
                .unremovable()
                .runtimeValue(recorder.createRetrievers(configNames, flatBuilders))
                .done());
        retrieverNames.produce(new CamelRetrieverNamesBuildItem(names));
    }

    /**
     * One bean per retriever, {@code @Named} for lookup and {@code @CamelRetrieverName} to drop the implicit
     * {@code @Default}. Unremovable: an augmentor resolves it programmatically.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void retrieverBeans(CamelRetrieverNamesBuildItem retrieverNames, CamelRetrieverRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        for (String name : retrieverNames.getNames()) {
            syntheticBeans.produce(SyntheticBeanBuildItem.configure(CamelContentRetriever.class)
                    .types(ContentRetriever.class, CamelContentRetriever.class)
                    .scope(Singleton.class)
                    .addQualifier().annotation(Named.class).addValue("value", name).done()
                    .addQualifier().annotation(CamelRetrieverName.class).addValue("value", name).done()
                    .unremovable()
                    .setRuntimeInit()
                    .supplier(recorder.retrieverSupplier(name))
                    .done());
        }
    }

    /** Creates the retrievers before the Camel context starts, so a wrong endpoint fails the startup. */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    CamelRuntimeTaskBuildItem initRetrievers(CamelRetrieverRecorder recorder, CamelContextBuildItem camelContext) {
        recorder.initRetrievers(camelContext.getCamelContext());
        return new CamelRuntimeTaskBuildItem("camel-retrievers");
    }
}
