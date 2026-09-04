package com.adn.dabaeum.common.infrastructure.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.SshClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApacheMinaSshPortForwardingClientTest {

    @TempDir
    Path tempDirectory;

    private EmbeddedSshServerSupport server;

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void missingKnownHostsFailsBeforeClientCreation() {
        AtomicBoolean factoryCalled = new AtomicBoolean();
        ApacheMinaSshPortForwardingClient client = new ApacheMinaSshPortForwardingClient(() -> {
            factoryCalled.set(true);
            throw new AssertionError("client factory must not be called");
        });
        SshTunnelProperties properties = propertiesWithoutServer(
            tempDirectory.resolve("missing_known_hosts"),
            EmbeddedSshServerSupport.availableLoopbackPort()
        );

        assertThatThrownBy(() -> client.open(properties))
            .isInstanceOf(SshTunnelException.class)
            .hasMessageContaining("known_hosts file does not exist");

        assertThat(factoryCalled).isFalse();
    }

    @Test
    void matchingHostKeyAndPasswordOpenTunnel() throws Exception {
        server = EmbeddedSshServerSupport.start(tempDirectory);
        Path knownHosts = server.writeKnownHosts(tempDirectory.resolve("known_hosts"));
        int localPort = EmbeddedSshServerSupport.availableLoopbackPort();

        try (ServerSocket remoteService = new ServerSocket(
            0, 1, InetAddress.getLoopbackAddress()
        )) {
            Thread echo = echoOnce(remoteService);
            ApacheMinaSshPortForwardingClient client = new ApacheMinaSshPortForwardingClient(
                SshClient::setUpDefaultClient
            );
            try {
                client.open(server.properties(
                    knownHosts,
                    EmbeddedSshServerSupport.PASSWORD,
                    localPort,
                    remoteService.getLocalPort()
                ));

                assertThat(client.isOpen()).isTrue();
                assertThat(request(localPort, "ping")).isEqualTo("pong");
                echo.join(5_000);
                assertThat(echo.isAlive()).isFalse();
            } finally {
                client.close();
            }
        }
    }

    @Test
    void mismatchedHostKeyRejectsConnection() throws Exception {
        server = EmbeddedSshServerSupport.start(tempDirectory);
        ApacheMinaSshPortForwardingClient client = new ApacheMinaSshPortForwardingClient(
            SshClient::setUpDefaultClient
        );
        try {
            assertThatThrownBy(() -> client.open(server.properties(
                server.writeMismatchedKnownHosts(tempDirectory.resolve("known_hosts")),
                EmbeddedSshServerSupport.availableLoopbackPort()
            )))
                .isInstanceOf(SshTunnelException.class);
            assertThat(client.isOpen()).isFalse();
        } finally {
            client.close();
        }
    }

    @Test
    void wrongPasswordRejectsAuthentication() throws Exception {
        server = EmbeddedSshServerSupport.start(tempDirectory);
        ApacheMinaSshPortForwardingClient client = new ApacheMinaSshPortForwardingClient(
            SshClient::setUpDefaultClient
        );
        try {
            assertThatThrownBy(() -> client.open(server.properties(
                server.writeKnownHosts(tempDirectory.resolve("known_hosts")),
                "wrong-password",
                EmbeddedSshServerSupport.availableLoopbackPort(),
                EmbeddedSshServerSupport.availableLoopbackPort()
            )))
                .isInstanceOf(SshTunnelException.class);
            assertThat(client.isOpen()).isFalse();
        } finally {
            client.close();
        }
    }

    @Test
    void occupiedLocalPortRejectsForwarding() throws Exception {
        server = EmbeddedSshServerSupport.start(tempDirectory);
        int occupiedPort;
        try (ServerSocket occupied = new ServerSocket(
            0, 1, InetAddress.getLoopbackAddress()
        )) {
            occupiedPort = occupied.getLocalPort();
            ApacheMinaSshPortForwardingClient client = new ApacheMinaSshPortForwardingClient(
                SshClient::setUpDefaultClient
            );
            try {
                assertThatThrownBy(() -> client.open(server.properties(
                    server.writeKnownHosts(tempDirectory.resolve("known_hosts")),
                    occupiedPort
                )))
                    .isInstanceOf(SshTunnelException.class);
                assertThat(client.isOpen()).isFalse();
            } finally {
                client.close();
            }
        }
    }

    @Test
    void closeIsIdempotentAndReleasesLocalPort() throws Exception {
        server = EmbeddedSshServerSupport.start(tempDirectory);
        int localPort = EmbeddedSshServerSupport.availableLoopbackPort();
        ApacheMinaSshPortForwardingClient client = new ApacheMinaSshPortForwardingClient(
            SshClient::setUpDefaultClient
        );
        try {
            client.open(server.properties(
                server.writeKnownHosts(tempDirectory.resolve("known_hosts")),
                localPort
            ));

            client.close();
            assertThat(client.isOpen()).isFalse();
            assertThatCode(client::close).doesNotThrowAnyException();
            try (ServerSocket released = new ServerSocket(
                localPort, 1, InetAddress.getLoopbackAddress()
            )) {
                assertThat(released.getLocalPort()).isEqualTo(localPort);
            }
        } finally {
            client.close();
        }
    }

    private SshTunnelProperties propertiesWithoutServer(Path knownHosts, int localPort) {
        return new SshTunnelProperties(
            true,
            EmbeddedSshServerSupport.HOST,
            2222,
            EmbeddedSshServerSupport.USERNAME,
            EmbeddedSshServerSupport.PASSWORD,
            EmbeddedSshServerSupport.HOST,
            localPort,
            EmbeddedSshServerSupport.HOST,
            5432,
            knownHosts,
            java.time.Duration.ofSeconds(5),
            java.time.Duration.ofSeconds(5)
        );
    }

    private static Thread echoOnce(ServerSocket remoteService) {
        Thread thread = new Thread(() -> {
            try (Socket socket = remoteService.accept();
                 BufferedReader input = new BufferedReader(
                     new InputStreamReader(socket.getInputStream())
                 );
                 BufferedWriter output = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream())
                 )) {
                assertThat(input.readLine()).isEqualTo("ping");
                output.write("pong");
                output.newLine();
                output.flush();
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
        thread.start();
        return thread;
    }

    private static String request(int localPort, String message) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), localPort);
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            output.write(message);
            output.newLine();
            output.flush();
            return input.readLine();
        }
    }
}
