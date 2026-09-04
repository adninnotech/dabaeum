package com.adn.dabaeum.common.infrastructure.ssh;

public interface SshPortForwardingClient extends AutoCloseable {

    void open(SshTunnelProperties properties);

    boolean isOpen();

    @Override
    void close();
}
