package com.adn.dabaeum.common.infrastructure.ssh;

import org.apache.sshd.client.SshClient;

public final class DefaultSshClientFactory implements SshClientFactory {

    @Override
    public SshClient create() {
        return SshClient.setUpDefaultClient();
    }
}
