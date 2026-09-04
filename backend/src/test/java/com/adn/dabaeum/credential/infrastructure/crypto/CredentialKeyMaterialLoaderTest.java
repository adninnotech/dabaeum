package com.adn.dabaeum.credential.infrastructure.crypto;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.domain.CredentialProofService;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/**
 * 개인키 파일의 모드가 정확히 소유자 읽기·쓰기(600)인지 검증하는 로더의 계약을 다룬다.
 *
 * <p>{@code CredentialKeyMaterialLoader}는 본체에서 {@code getPosixFilePermissions}로
 * 파일 모드를 확인하므로 POSIX 파일 속성을 제공하지 않는 파일시스템(예: Windows)에서는
 * 로더 자체가 동작하지 않는다. 해당 환경에서는 실패 대신 건너뛴다.
 */
@EnabledIf("posixFileAttributesSupported")
class CredentialKeyMaterialLoaderTest {

    static boolean posixFileAttributesSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    @TempDir
    Path tempDirectory;

    private final CredentialKeyMaterialLoader loader = new CredentialKeyMaterialLoader();

    @Test
    void loadsPkcs8AndX509Ed25519KeysAndVerifiesTheirMatch() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privatePath = writePem("private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicPath = writePem("public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        Files.setPosixFilePermissions(privatePath, Set.of(OWNER_READ, OWNER_WRITE));

        CredentialKeyMaterialLoader.KeyMaterial material = loader.load(privatePath, publicPath);

        assertThat(material.privateKey().getAlgorithm()).isEqualTo("EdDSA");
        assertThat(material.publicKey().getAlgorithm()).isEqualTo("EdDSA");
    }

    @Test
    void loadsPublicKeyWithoutReadingPrivateKey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path publicPath = writePem("public-only.pem", "PUBLIC KEY",
            pair.getPublic().getEncoded());

        PublicKey publicKey = loader.loadPublic(publicPath);

        assertThat(publicKey.getAlgorithm()).isEqualTo("EdDSA");
    }

    @Test
    void rejectsPrivateKeyUnlessItsExactModeIsOwnerReadWrite() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privatePath = writePem("private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicPath = writePem("public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        Files.setPosixFilePermissions(privatePath, Set.of(OWNER_READ));

        assertSafeFailure(() -> loader.load(privatePath, publicPath), privatePath, publicPath);
    }

    @Test
    void rejectsSymbolicLinksAndMismatchedKeyPairs() throws Exception {
        KeyPair first = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair second = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privatePath = writePem("private.pem", "PRIVATE KEY", first.getPrivate().getEncoded());
        Path publicPath = writePem("public.pem", "PUBLIC KEY", second.getPublic().getEncoded());
        Files.setPosixFilePermissions(privatePath, Set.of(OWNER_READ, OWNER_WRITE));

        assertSafeFailure(() -> loader.load(privatePath, publicPath), privatePath, publicPath);

        Path link = tempDirectory.resolve("private-link.pem");
        Files.createSymbolicLink(link, privatePath);
        assertSafeFailure(() -> loader.load(link, publicPath), link, publicPath);
    }

    @Test
    void emptyExternalPathsDoNotCreateSigningBean() {
        new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(CredentialProofConfiguration.class)
            .withPropertyValues(
                "dabaeum.credential-proof.key-id=urn:dabaeum:credential-key:development-1",
                "dabaeum.credential-proof.private-key-path=",
                "dabaeum.credential-proof.public-key-path=")
            .run(context -> assertThat(context).doesNotHaveBean(CredentialProofService.class));
    }

    @Test
    void publicKeyPathAlonePublishesPublicKeyWithoutSigningBean() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path publicPath = writePem("public-config.pem", "PUBLIC KEY",
            pair.getPublic().getEncoded());

        new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(CredentialProofConfiguration.class)
            .withPropertyValues(
                "dabaeum.credential-proof.key-id=urn:dabaeum:credential-key:development-1",
                "dabaeum.credential-proof.private-key-path=",
                "dabaeum.credential-proof.public-key-path=" + publicPath)
            .run(context -> {
                assertThat(context).hasSingleBean(PublicKey.class);
                assertThat(context).doesNotHaveBean(CredentialProofService.class);
            });
    }

    private Path writePem(String fileName, String type, byte[] encoded) throws Exception {
        Path path = tempDirectory.resolve(fileName);
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
            + "\n-----END " + type + "-----\n");
        return path;
    }

    private void assertSafeFailure(ThrowingCall call, Path first, Path second) {
        assertThatThrownBy(call::run).satisfies(exception ->
            assertThat(exception.getMessage())
                .doesNotContain(first.toString(), second.toString(), "PRIVATE KEY", "PUBLIC KEY"));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
