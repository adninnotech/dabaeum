package com.adn.dabaeum.credential.infrastructure.crypto;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

public class CredentialKeyMaterialLoader {

    private static final byte[] KEY_MATCH_CHALLENGE =
        "dabaeum-credential-key-match".getBytes(StandardCharsets.US_ASCII);

    public KeyMaterial load(Path privateKeyPath, Path publicKeyPath) {
        try {
            requireRegularFile(privateKeyPath);
            requireRegularFile(publicKeyPath);
            if (!Files.getPosixFilePermissions(privateKeyPath, NOFOLLOW_LINKS)
                .equals(Set.of(OWNER_READ, OWNER_WRITE))) {
                throw new IllegalArgumentException("private key permissions are invalid");
            }

            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                readPem(privateKeyPath, "PRIVATE KEY")));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(
                readPem(publicKeyPath, "PUBLIC KEY")));
            verifyKeyPair(privateKey, publicKey);
            return new KeyMaterial(privateKey, publicKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Credential proof key material is invalid");
        }
    }

    public PublicKey loadPublic(Path publicKeyPath) {
        try {
            requireRegularFile(publicKeyPath);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
                readPem(publicKeyPath, "PUBLIC KEY")));
        } catch (Exception exception) {
            throw new IllegalStateException("Credential proof public key is invalid");
        }
    }

    private void requireRegularFile(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("key file is invalid");
        }
    }

    private byte[] readPem(Path path, String type) throws Exception {
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int beginIndex = pem.indexOf(begin);
        int endIndex = pem.indexOf(end);
        if (beginIndex != 0 || endIndex <= begin.length()
            || !pem.substring(endIndex + end.length()).trim().isEmpty()) {
            throw new IllegalArgumentException("key encoding is invalid");
        }
        String encoded = pem.substring(begin.length(), endIndex).replaceAll("\\s", "");
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("key encoding is invalid");
        }
        return Base64.getDecoder().decode(encoded);
    }

    private void verifyKeyPair(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(KEY_MATCH_CHALLENGE);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(KEY_MATCH_CHALLENGE);
        if (!verifier.verify(signature)) {
            throw new IllegalArgumentException("key pair does not match");
        }
    }

    public record KeyMaterial(PrivateKey privateKey, PublicKey publicKey) {
        public KeyMaterial {
            Objects.requireNonNull(privateKey, "privateKey");
            Objects.requireNonNull(publicKey, "publicKey");
        }
    }
}
