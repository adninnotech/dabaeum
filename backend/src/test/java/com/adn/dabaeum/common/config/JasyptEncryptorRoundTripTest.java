package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.junit.jupiter.api.Test;

class JasyptEncryptorRoundTripTest {

    @Test
    void encryptsAndDecryptsAnEncWrappedNonsecretFixture() {
        String fixturePassword = "fixture-value";
        StandardPBEStringEncryptor encryptor = configuredEncryptor();

        String ciphertext = encryptor.encrypt(fixturePassword);
        String encryptedProperty = "ENC(" + ciphertext + ")";

        assertThat(encryptedProperty)
            .startsWith("ENC(")
            .endsWith(")");
        assertThat(ciphertext).doesNotContain(fixturePassword);

        String decrypted = encryptor.decrypt(
            encryptedProperty.substring(4, encryptedProperty.length() - 1)
        );
        assertThat(decrypted).isEqualTo(fixturePassword);
    }

    private StandardPBEStringEncryptor configuredEncryptor() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword("test-encryptor-password");
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setKeyObtentionIterations(1000);
        encryptor.setProviderName("SunJCE");
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setStringOutputType("base64");
        return encryptor;
    }
}
