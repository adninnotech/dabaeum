package com.adn.dabaeum.common.infrastructure.ssh;

import java.util.Objects;

public final class SshTunnelManager implements AutoCloseable {

    private final SshTunnelProperties properties;
    private final SshPortForwardingClient client;
    private boolean started;
    private boolean ready;
    private boolean closed;

    public SshTunnelManager(
        SshTunnelProperties properties,
        SshPortForwardingClient client
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.client = Objects.requireNonNull(client);
    }

    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("SSH tunnel manager is already closed");
        }
        if (started) {
            return;
        }

        try {
            client.open(properties);
            if (!client.isOpen()) {
                throw new SshTunnelException("SSH tunnel client did not become open");
            }
            started = true;
            ready = true;
        } catch (RuntimeException exception) {
            ready = false;
            RuntimeException failure = exception instanceof SshTunnelException
                ? exception
                : new SshTunnelException("Failed to open SSH tunnel", exception);
            try {
                client.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public synchronized boolean isReady() {
        return ready && client.isOpen();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        ready = false;

        if (started || client.isOpen()) {
            client.close();
        }
        closed = true;
    }
}
