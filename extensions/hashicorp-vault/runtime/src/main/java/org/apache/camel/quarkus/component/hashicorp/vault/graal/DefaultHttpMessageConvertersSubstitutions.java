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
package org.apache.camel.quarkus.component.hashicorp.vault.graal;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.JsonbHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.ClassUtils;

/**
 * Substitute DefaultBuilder.detectMessageConverters() to drop the CBOR, feed, protobuf, Smile, XML and YAML branches.
 * Those converters are not shaded into camel-quarkus-support-spring-web, and native image links the method eagerly, so
 * the absent types break image generation even though the branches are never taken at runtime.
 */
@TargetClass(className = "org.springframework.http.converter.DefaultHttpMessageConverters", innerClass = "DefaultBuilder")
final class DefaultHttpMessageConvertersSubstitutions {

    @Alias
    ByteArrayHttpMessageConverter byteArrayConverter;

    @Alias
    HttpMessageConverter<?> stringConverter;

    @Alias
    HttpMessageConverter<?> jsonConverter;

    @Substitute
    void detectMessageConverters() {
        this.byteArrayConverter = new ByteArrayHttpMessageConverter();

        if (this.stringConverter == null) {
            this.stringConverter = new StringHttpMessageConverter();
        }
        if (this.jsonConverter == null) {
            if (JsonLibraries.JACKSON_PRESENT) {
                this.jsonConverter = new JacksonJsonHttpMessageConverter();
            } else if (JsonLibraries.JACKSON_2_PRESENT) {
                this.jsonConverter = new MappingJackson2HttpMessageConverter();
            } else if (JsonLibraries.GSON_PRESENT) {
                this.jsonConverter = new GsonHttpMessageConverter();
            } else if (JsonLibraries.JSONB_PRESENT) {
                this.jsonConverter = new JsonbHttpMessageConverter();
            }
        }
    }
}

final class JsonLibraries {

    static final ClassLoader LOADER = JsonLibraries.class.getClassLoader();
    static final boolean JACKSON_PRESENT = ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", LOADER);
    static final boolean JACKSON_2_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", LOADER)
            && ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", LOADER);
    static final boolean GSON_PRESENT = ClassUtils.isPresent("com.google.gson.Gson", LOADER);
    static final boolean JSONB_PRESENT = ClassUtils.isPresent("jakarta.json.bind.Jsonb", LOADER);

    private JsonLibraries() {
    }
}
