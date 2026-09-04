package com.adn.dabaeum.common.infrastructure.ssh;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.KeyPairGenerator;
import java.time.Duration;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

final class EmbeddedSshServerSupport implements AutoCloseable {

    static final String HOST = "127.0.0.1";
    static final String USERNAME = "test-user";
    static final String PASSWORD = "correct-password";

    private final SshServer server;

    private EmbeddedSshServerSupport(SshServer server) {
        this.server = server;
    }

    static EmbeddedSshServerSupport start(Path tempDirectory) throws IOException {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost(HOST);
        server.setPort(0);
        server.setKeyPairProvider(
            new SimpleGeneratorHostKeyProvider(tempDirectory.resolve("hostkey.ser"))
        );
        server.setPasswordAuthenticator(
            (username, password, session) ->
                USERNAME.equals(username) && PASSWORD.equals(password)
        );
        server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        server.start();
        return new EmbeddedSshServerSupport(server);
    }

    int port() {
        return server.getPort();
    }

    Path writeKnownHosts(Path path) throws Exception {
        PublicKey publicKey = server.getKeyPairProvider().loadKeys(null)
            .iterator()
            .next()
            .getPublic();
        Files.writeString(
            path,
            "[" + HOST + "]:" + port() + " " + PublicKeyEntry.toString(publicKey)
                + System.lineSeparator()
        );
        return path;
    }

    Path writeMismatchedKnownHosts(Path path) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        Files.writeString(
            path,
            "[" + HOST + "]:" + port()
                + " " + PublicKeyEntry.toString(keyPairGenerator.generateKeyPair().getPublic())
                + System.lineSeparator()
        );
        return path;
    }

    SshTunnelProperties properties(Path knownHosts, int localPort) {
        return new SshTunnelProperties(
            true,
            HOST,
            port(),
            USERNAME,
            PASSWORD,
            HOST,
            localPort,
            HOST,
            availableLoopbackPort(),
            knownHosts,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5)
        );
    }

    SshTunnelProperties properties(
        Path knownHosts,
        String password,
        int localPort,
        int remotePort
    ) {
        return new SshTunnelProperties(
            true,
            HOST,
            port(),
            USERNAME,
            password,
            HOST,
            localPort,
            HOST,
            remotePort,
            knownHosts,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5)
        );
    }

    static int availableLoopbackPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not allocate a loopback port", exception);
        }
    }

    @Override
    public void close() throws IOException {
        server.stop(true);
    }
}
