package com.adn.dabaeum.common.infrastructure.ssh;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SshTunnelPropertiesValidationTest {

    @ParameterizedTest
    @MethodSource("blankTextCases")
    void rejectsBlankTextFields(String fieldName, Consumer<PropertiesBuilder> invalidValue) {
        assertThatThrownBy(() -> {
            PropertiesBuilder builder = validBuilder();
            invalidValue.accept(builder);
            builder.build();
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(fieldName);
    }

    private static Object[][] blankTextCases() {
        return new Object[][] {
            {"host", (Consumer<PropertiesBuilder>) builder -> builder.host(" ")},
            {"username", (Consumer<PropertiesBuilder>) builder -> builder.username(" ")},
            {"password", (Consumer<PropertiesBuilder>) builder -> builder.password(" ")},
            {"localHost", (Consumer<PropertiesBuilder>) builder -> builder.localHost(" ")},
            {"remoteHost", (Consumer<PropertiesBuilder>) builder -> builder.remoteHost(" ")}
        };
    }

    @ParameterizedTest
    @MethodSource("invalidPortCases")
    void rejectsPortOutsideInclusiveRange(String fieldName, Consumer<PropertiesBuilder> invalidValue) {
        assertThatThrownBy(() -> {
            PropertiesBuilder builder = validBuilder();
            invalidValue.accept(builder);
            builder.build();
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(fieldName);
    }

    private static Object[][] invalidPortCases() {
        return new Object[][] {
            {"port", (Consumer<PropertiesBuilder>) builder -> builder.port(0)},
            {"port", (Consumer<PropertiesBuilder>) builder -> builder.port(65536)},
            {"localPort", (Consumer<PropertiesBuilder>) builder -> builder.localPort(0)},
            {"localPort", (Consumer<PropertiesBuilder>) builder -> builder.localPort(65536)},
            {"remotePort", (Consumer<PropertiesBuilder>) builder -> builder.remotePort(0)},
            {"remotePort", (Consumer<PropertiesBuilder>) builder -> builder.remotePort(65536)}
        };
    }

    @Test
    void rejectsNullKnownHostsPath() {
        assertThatThrownBy(() -> validBuilder()
            .knownHostsPath(null)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("knownHostsPath");
    }

    @ParameterizedTest
    @MethodSource("invalidConnectTimeouts")
    void rejectsInvalidConnectTimeout(Duration timeout) {
        assertThatThrownBy(() -> validBuilder()
            .connectTimeout(timeout)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("connectTimeout");
    }

    private static Duration[] invalidConnectTimeouts() {
        return new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1)};
    }

    @ParameterizedTest
    @MethodSource("invalidAuthTimeouts")
    void rejectsInvalidAuthTimeout(Duration timeout) {
        assertThatThrownBy(() -> validBuilder()
            .authTimeout(timeout)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authTimeout");
    }

    private static Duration[] invalidAuthTimeouts() {
        return new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1)};
    }

    static PropertiesBuilder validBuilder() {
        return new PropertiesBuilder();
    }

    static final class PropertiesBuilder {

        private boolean enabled = true;
        private String host = "ssh.example.test";
        private int port = 22;
        private String username = "tunnel-user";
        private String password = "tunnel-password";
        private String localHost = "127.0.0.1";
        private int localPort = 15432;
        private String remoteHost = "database.internal";
        private int remotePort = 5432;
        private Path knownHostsPath = Path.of("/tmp/known_hosts");
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration authTimeout = Duration.ofSeconds(5);

        PropertiesBuilder host(String value) {
            host = value;
            return this;
        }

        PropertiesBuilder port(int value) {
            port = value;
            return this;
        }

        PropertiesBuilder username(String value) {
            username = value;
            return this;
        }

        PropertiesBuilder password(String value) {
            password = value;
            return this;
        }

        PropertiesBuilder localHost(String value) {
            localHost = value;
            return this;
        }

        PropertiesBuilder localPort(int value) {
            localPort = value;
            return this;
        }

        PropertiesBuilder remoteHost(String value) {
            remoteHost = value;
            return this;
        }

        PropertiesBuilder remotePort(int value) {
            remotePort = value;
            return this;
        }

        PropertiesBuilder knownHostsPath(Path value) {
            knownHostsPath = value;
            return this;
        }

        PropertiesBuilder connectTimeout(Duration value) {
            connectTimeout = value;
            return this;
        }

        PropertiesBuilder authTimeout(Duration value) {
            authTimeout = value;
            return this;
        }

        SshTunnelProperties build() {
            return new SshTunnelProperties(
                enabled,
                host,
                port,
                username,
                password,
                localHost,
                localPort,
                remoteHost,
                remotePort,
                knownHostsPath,
                connectTimeout,
                authTimeout
            );
        }
    }
}
