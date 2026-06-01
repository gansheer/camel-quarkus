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
 * KIND, either express or implied.  See the License for the specific language governing permissions and
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
import java.security.PublicKey;
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

/**
 * Generates X.509 TLS certificates for NATS server testing using BouncyCastle LTS (bcprov-lts8on).
 * This custom generator avoids the dependency conflict between jnats (requires bcprov-lts8on)
 * and smallrye-certificate-generator (requires bcprov-jdk18on).
 */
public class NatsCertificateGenerator {

    private final Path certsDir;
    private KeyPair caKeyPair;
    private X509Certificate caCertificate;
    private KeyPair serverKeyPair;
    private X509Certificate serverCertificate;

    public NatsCertificateGenerator(Path certsDir) {
        this.certsDir = certsDir;
    }

    /**
     * Generate all required certificates for NATS TLS testing.
     */
    public static NatsCertificateGenerator generate(Path certsDir) throws Exception {
        NatsCertificateGenerator generator = new NatsCertificateGenerator(certsDir);
        generator.generateAll();
        return generator;
    }

    private void generateAll() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        Files.createDirectories(certsDir);

        caKeyPair = generateRsaKeyPair(2048);
        caCertificate = generateCACertificate(caKeyPair, "CN=NATS Test CA");
       writePemFile(caCertificate, certsDir.resolve("nats-ca.crt"));

        serverKeyPair = generateRsaKeyPair(2048);
        serverCertificate = generateServerCertificate(
                serverKeyPair.getPublic(),
                caKeyPair.getPrivate(),
                caCertificate,
                "CN=localhost",
                "localhost");
        writePemFile(serverCertificate, certsDir.resolve("nats.crt"));
        writePemFile(serverKeyPair.getPrivate(), certsDir.resolve("nats.key"));

        createKeyStore(
                serverKeyPair.getPrivate(),
                new X509Certificate[] { serverCertificate, caCertificate },
                certsDir.resolve("nats-keystore.p12"),
                "password");
        createTrustStore(
                caCertificate,
                certsDir.resolve("nats-truststore.p12"),
                "password");
    }

    /**
     * Generate an RSA key pair.
     */
    private KeyPair generateRsaKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * Generate a self-signed CA certificate.
     */
    private X509Certificate generateCACertificate(KeyPair keyPair, String subject) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86400000L); // 1 day ago
        Date notAfter = new Date(now + 31536000000L); // 1 year from now

        X500Name issuer = new X500Name(subject);
        BigInteger serial = BigInteger.valueOf(now);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                issuer,
                keyPair.getPublic());

        certBuilder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(true));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);
    }

    /**
     * Generate a server certificate signed by the CA.
     */
    private X509Certificate generateServerCertificate(
            PublicKey serverPublicKey,
            PrivateKey caPrivateKey,
            X509Certificate caCert,
            String subject,
            String hostname) throws Exception {

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86400000L); // 1 day ago
        Date notAfter = new Date(now + 31536000000L); // 1 year from now

        X500Name issuer = new X500Name(caCert.getSubjectX500Principal().getName());
        X500Name subjectName = new X500Name(subject);
        BigInteger serial = BigInteger.valueOf(now);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subjectName,
                serverPublicKey);

        GeneralNames subjectAltNames = new GeneralNames(
                new GeneralName(GeneralName.dNSName, hostname));
        certBuilder.addExtension(
                Extension.subjectAlternativeName,
                false,
                subjectAltNames);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(caPrivateKey);

        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);
    }

    /**
     * Write a certificate or private key to a PEM file.
     */
    private void writePemFile(Object object, Path path) throws IOException {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(path.toFile()))) {
            pemWriter.writeObject(object);
        }
    }

    /**
     * Create a PKCS12 keystore containing a private key and certificate chain.
     */
    private void createKeyStore(PrivateKey privateKey, X509Certificate[] certChain, Path keystorePath, String password)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("nats", privateKey, password.toCharArray(), certChain);
        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            keyStore.store(fos, password.toCharArray());
        }
    }

    /**
     * Create a PKCS12 truststore containing trusted CA certificates.
     */
    private void createTrustStore(X509Certificate caCert, Path truststorePath, String password) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("nats-ca", caCert);
        try (FileOutputStream fos = new FileOutputStream(truststorePath.toFile())) {
            trustStore.store(fos, password.toCharArray());
        }
    }

    public Path getCaCertPath() {
        return certsDir.resolve("nats-ca.crt");
    }

    public Path getServerCertPath() {
        return certsDir.resolve("nats.crt");
    }

    public Path getServerKeyPath() {
        return certsDir.resolve("nats.key");
    }

    public Path getKeyStorePath() {
        return certsDir.resolve("nats-keystore.p12");
    }

    public Path getTrustStorePath() {
        return certsDir.resolve("nats-truststore.p12");
    }
}
