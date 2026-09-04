package com.adn.dabaeum.common.infrastructure.ssh;

public final class SshTunnelException extends RuntimeException {

    public SshTunnelException(String message) {
        super(message);
    }

    public SshTunnelException(String message, Throwable cause) {
        super(message, cause);
    }
}
