package dev.p2pchat;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

public class InMemoryKeyStore {
     static KeyStore generateInMemoryKeyStore(String cn) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, IOException {
        Security.addProvider(new BouncyCastleProvider());

        // Генерируем RSA ключи
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        // Создаём self-signed сертификат
        long now = System.currentTimeMillis();
        X500Name issuer = new X500Name("CN=" + cn);
        BigInteger serial = BigInteger.valueOf(now);
        Date notBefore = new Date(now - 24 * 60 * 60 * 1000); // вчера
        Date notAfter = new Date(now + 365 * 24 * 60 * 60 * 1000L); // через год

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        // Складываем в KeyStore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("servercert", keyPair.getPrivate(),
                "secret".toCharArray(), new X509Certificate[]{cert});

        return keyStore;
    }
}
