package com.adn.dabaeum.common.infrastructure.ssh;

import org.apache.sshd.client.SshClient;

@FunctionalInterface
public interface SshClientFactory {

    SshClient create();
}
