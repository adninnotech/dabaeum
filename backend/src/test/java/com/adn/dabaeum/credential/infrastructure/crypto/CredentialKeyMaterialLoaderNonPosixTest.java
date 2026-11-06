package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * POSIX 파일 속성이 없는 파일시스템(Windows, 테스트베드 PC)에서는 파일 모드 개념이 없으므로
 * 모드 검사를 건너뛰고 키를 읽는다. 나머지 검사(형식·키 쌍 일치·심볼릭 링크)는 그대로다.
 */
@DisabledIf("posixFileAttributesSupported")
class CredentialKeyMaterialLoaderNonPosixTest {

    static boolean posixFileAttributesSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    @TempDir
    Path tempDirectory;

    private final CredentialKeyMaterialLoader loader = new CredentialKeyMaterialLoader();

    @Test
    void loadsKeysWithoutPosixModeCheck() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privatePath = writePem("private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicPath = writePem("public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        CredentialKeyMaterialLoader.KeyMaterial material = loader.load(privatePath, publicPath);

        assertThat(material.privateKey().getAlgorithm()).isEqualTo("EdDSA");
        assertThat(material.publicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void stillRejectsMismatchedKeyPairs() throws Exception {
        KeyPair first = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair second = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privatePath = writePem("private.pem", "PRIVATE KEY", first.getPrivate().getEncoded());
        Path publicPath = writePem("public.pem", "PUBLIC KEY", second.getPublic().getEncoded());

        assertThatThrownBy(() -> loader.load(privatePath, publicPath))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Credential proof key material is invalid");
    }

    private Path writePem(String name, String type, byte[] encoded) throws Exception {
        Path path = tempDirectory.resolve(name);
        Files.writeString(path, "-----BEGIN " + type + "-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
            + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
        return path;
    }
}
