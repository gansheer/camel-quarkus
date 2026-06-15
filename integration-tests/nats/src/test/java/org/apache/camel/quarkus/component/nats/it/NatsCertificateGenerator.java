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
package org.apache.camel.quarkus.component.nats.it;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates X.509 TLS certificates for NATS server testing using BouncyCastle LTS (bcpkix-lts8on).
 * This avoids the dependency conflict between jnats (requires bcprov-lts8on) and
 * smallrye-certificate-generator (requires bcprov-jdk18on).
 */
final class NatsCertificateGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(NatsCertificateGenerator.class);

    static final String CERTS_DIR = "target/certs";
    static final String CA_CRT_PATH = CERTS_DIR + "/nats-ca.crt";
    static final String NATS_CRT_PATH = CERTS_DIR + "/nats.crt";
    static final String NATS_KEY_PATH = CERTS_DIR + "/nats.key";
    static final String KEYSTORE_PATH = CERTS_DIR + "/nats-keystore.p12";
    static final String TRUSTSTORE_PATH = CERTS_DIR + "/nats-truststore.p12";

    private static final String PASSWORD = "password";

    private NatsCertificateGenerator() {
    }

    static void generate(Path certsDir) throws Exception {
        if (Files.exists(certsDir.resolve("nats-keystore.p12"))) {
            LOG.info("Certificates already exist in {}, skipping generation", certsDir);
            return;
        }

        LOG.info("Generating NATS test certificates in {}", certsDir);
        Security.addProvider(new BouncyCastleProvider());
        Files.createDirectories(certsDir);

        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = generateCaCertificate(caKeyPair);
        writePem(caCert, certsDir.resolve("nats-ca.crt"));

        KeyPair serverKeyPair = generateRsaKeyPair();
        X509Certificate serverCert = generateServerCertificate(
                serverKeyPair, caKeyPair.getPrivate(), caCert, "localhost");
        writePem(serverCert, certsDir.resolve("nats.crt"));
        writePem(serverKeyPair.getPrivate(), certsDir.resolve("nats.key"));

        createKeyStore(serverKeyPair.getPrivate(),
                new X509Certificate[] { serverCert, caCert },
                certsDir.resolve("nats-keystore.p12"));
        createTrustStore(caCert, certsDir.resolve("nats-truststore.p12"));

        LOG.info("NATS test certificates generated successfully");
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
        generator.initialize(2048, new SecureRandom());
        return generator.generateKeyPair();
    }

    private static X509Certificate generateCaCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        X500Name name = new X500Name("CN=NATS Test CA");

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                new Date(now - 86400000L),
                new Date(now + 31536000000L),
                name,
                keyPair.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        return sign(builder, keyPair.getPrivate());
    }

    private static X509Certificate generateServerCertificate(
            KeyPair serverKeyPair, PrivateKey caPrivateKey,
            X509Certificate caCert, String hostname) throws Exception {

        long now = System.currentTimeMillis();

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(caCert.getSubjectX500Principal().getName()),
                BigInteger.valueOf(now + 1),
                new Date(now - 86400000L),
                new Date(now + 31536000000L),
                new X500Name("CN=" + hostname),
                serverKeyPair.getPublic());

        builder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName[] {
                        new GeneralName(GeneralName.dNSName, hostname),
                        new GeneralName(GeneralName.iPAddress, "127.0.0.1")
                }));

        return sign(builder, caPrivateKey);
    }

    private static X509Certificate sign(X509v3CertificateBuilder builder, PrivateKey key) throws Exception {
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(key);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(holder);
    }

    private static void writePem(Object object, Path path) throws IOException {
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(path.toFile()))) {
            writer.writeObject(object);
        }
    }

    private static void createKeyStore(PrivateKey privateKey, X509Certificate[] chain, Path path) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("nats", privateKey, PASSWORD.toCharArray(), chain);
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            keyStore.store(fos, PASSWORD.toCharArray());
        }
    }

    private static void createTrustStore(X509Certificate caCert, Path path) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("nats-ca", caCert);
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            trustStore.store(fos, PASSWORD.toCharArray());
        }
    }
}
