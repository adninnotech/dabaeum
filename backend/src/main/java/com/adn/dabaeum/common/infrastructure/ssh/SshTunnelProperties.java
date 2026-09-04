package com.adn.dabaeum.common.infrastructure.ssh;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dabaeum.ssh-tunnel")
public record SshTunnelProperties(
    boolean enabled,
    @NotBlank String host,
    @Min(1) @Max(65535) int port,
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank String localHost,
    @Min(1) @Max(65535) int localPort,
    @NotBlank String remoteHost,
    @Min(1) @Max(65535) int remotePort,
    @NotNull Path knownHostsPath,
    @NotNull Duration connectTimeout,
    @NotNull Duration authTimeout
) {
    public SshTunnelProperties {
        requireText(host, "host");
        requirePort(port, "port");
        requireText(username, "username");
        requireText(password, "password");
        requireText(localHost, "localHost");
        requirePort(localPort, "localPort");
        requireText(remoteHost, "remoteHost");
        requirePort(remotePort, "remotePort");

        if (knownHostsPath == null) {
            throw new IllegalArgumentException("knownHostsPath must not be null");
        }

        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(authTimeout, "authTimeout");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requirePort(int value, String name) {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(name + " must be between 1 and 65535");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
