package com.adn.dabaeum.common.infrastructure.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;

public final class ApacheMinaSshPortForwardingClient implements SshPortForwardingClient {

    private final SshClientFactory clientFactory;
    private SshClient client;
    private ClientSession session;
    private SshdSocketAddress boundAddress;

    public ApacheMinaSshPortForwardingClient(SshClientFactory clientFactory) {
        this.clientFactory = Objects.requireNonNull(clientFactory);
    }

    @Override
    public synchronized void open(SshTunnelProperties properties) {
        Objects.requireNonNull(properties);
        if (!Files.isRegularFile(properties.knownHostsPath())) {
            throw new SshTunnelException(
                "known_hosts file does not exist: " + properties.knownHostsPath()
            );
        }

        try {
            client = clientFactory.create();
            client.setServerKeyVerifier(
                new DefaultKnownHostsServerKeyVerifier(
                    RejectAllServerKeyVerifier.INSTANCE,
                    true,
                    properties.knownHostsPath()
                )
            );
            client.start();

            session = client.connect(
                    properties.username(),
                    properties.host(),
                    properties.port()
                )
                .verify(properties.connectTimeout())
                .getSession();
            session.addPasswordIdentity(properties.password());
            session.auth().verify(properties.authTimeout());
            boundAddress = session.startLocalPortForwarding(
                new SshdSocketAddress(properties.localHost(), properties.localPort()),
                new SshdSocketAddress(properties.remoteHost(), properties.remotePort())
            );
        } catch (Exception exception) {
            try {
                closeResources();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw new SshTunnelException(
                "Failed to open SSH tunnel to " + properties.host() + ":" + properties.port(),
                exception
            );
        }
    }

    @Override
    public synchronized boolean isOpen() {
        return client != null
            && client.isOpen()
            && session != null
            && session.isOpen()
            && boundAddress != null;
    }

    @Override
    public synchronized void close() {
        closeResources();
    }

    private void closeResources() {
        RuntimeException failure = null;
        if (session != null && boundAddress != null) {
            try {
                session.stopLocalPortForwarding(boundAddress);
            } catch (IOException | RuntimeException exception) {
                failure = cleanupFailure(failure, exception);
            }
        }
        if (session != null) {
            try {
                session.close(false);
            } catch (RuntimeException exception) {
                failure = cleanupFailure(failure, exception);
            }
        }
        if (client != null) {
            try {
                client.stop();
            } catch (RuntimeException exception) {
                failure = cleanupFailure(failure, exception);
            }
            try {
                client.close();
            } catch (IOException | RuntimeException exception) {
                failure = cleanupFailure(failure, exception);
            }
        }
        boundAddress = null;
        session = null;
        client = null;

        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException cleanupFailure(RuntimeException previous, Exception exception) {
        RuntimeException current = exception instanceof RuntimeException runtimeException
            ? runtimeException
            : new SshTunnelException("Failed to close SSH tunnel", exception);
        if (previous != null) {
            previous.addSuppressed(current);
            return previous;
        }
        return current;
    }
}
