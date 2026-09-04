package com.adn.dabaeum.common.infrastructure.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SshTunnelManagerTest {

    @Test
    void startOpensClientOnlyOnceAndCloseReleasesIt() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);

        manager.start();
        manager.start();

        assertThat(client.openCount).isEqualTo(1);
        assertThat(manager.isReady()).isTrue();

        manager.close();
        manager.close();

        assertThat(client.closeCount).isEqualTo(1);
        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void failedOpenDoesNotMarkManagerReady() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        client.failOnOpen = true;

        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);

        assertThatThrownBy(manager::start)
            .isInstanceOf(SshTunnelException.class);

        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void rejectsStartAfterSuccessfulClose() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);

        manager.start();
        manager.close();

        assertThatThrownBy(manager::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already closed");
    }

    @Test
    void partialOpenFailureCleansClientAndPreservesBothFailures() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        RuntimeException openFailure = new IllegalStateException("simulated partial open failure");
        RuntimeException closeFailure = new IllegalStateException("simulated cleanup failure");
        client.openFailure = openFailure;
        client.openBeforeFailure = true;
        client.closeFailuresRemaining = 1;
        client.closeFailure = closeFailure;
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);

        Throwable thrown = catchThrowable(manager::start);

        assertThat(thrown).isInstanceOf(SshTunnelException.class);
        assertThat(thrown.getCause()).isSameAs(openFailure);
        assertThat(thrown.getSuppressed()).containsExactly(closeFailure);
        assertThat(client.closeCount).isEqualTo(1);
        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void isOpenFailureCleansClientAndLeavesManagerNotReady() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        RuntimeException isOpenFailure = new IllegalStateException("simulated isOpen failure");
        client.isOpenFailure = isOpenFailure;
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);

        Throwable thrown = catchThrowable(manager::start);

        assertThat(thrown).isInstanceOf(SshTunnelException.class);
        assertThat(thrown.getCause()).isSameAs(isOpenFailure);
        assertThat(client.closeCount).isEqualTo(1);
        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void clientThatDoesNotBecomeOpenIsCleanedAndLeavesManagerNotReady() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        client.remainClosedAfterOpen = true;
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);

        assertThatThrownBy(manager::start)
            .isInstanceOf(SshTunnelException.class)
            .hasMessageContaining("did not become open");

        assertThat(client.closeCount).isEqualTo(1);
        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void failedCloseCanBeRetried() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        RuntimeException closeFailure = new IllegalStateException("simulated close failure");
        client.closeFailuresRemaining = 1;
        client.closeFailure = closeFailure;
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);
        manager.start();

        assertThatThrownBy(manager::close).isSameAs(closeFailure);
        assertThat(manager.isReady()).isFalse();

        manager.close();

        assertThat(client.closeCount).isEqualTo(2);
        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void externallyClosedClientMakesManagerNotReady() {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);
        manager.start();

        client.closeExternally();

        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void concurrentStartAndCloseCallsOpenAndCloseClientOnce() throws Exception {
        FakeSshPortForwardingClient client = new FakeSshPortForwardingClient();
        CountDownLatch firstOpenEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstOpen = new CountDownLatch(1);
        CountDownLatch competingCallsReady = new CountDownLatch(7);
        client.beforeOpen = () -> {
            firstOpenEntered.countDown();
            await(releaseFirstOpen);
        };
        SshTunnelManager manager = new SshTunnelManager(validProperties(), client);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            Future<?> firstStart = executor.submit(manager::start);
            assertThat(firstOpenEntered.await(5, TimeUnit.SECONDS)).isTrue();

            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                futures.add(executor.submit(() -> {
                    competingCallsReady.countDown();
                    try {
                        manager.start();
                    } catch (IllegalStateException ignored) {
                        // 경합에서 close가 먼저 완료되면 이후 start는 유효하지 않다.
                    }
                }));
            }
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(() -> {
                    competingCallsReady.countDown();
                    manager.close();
                }));
            }

            assertThat(competingCallsReady.await(5, TimeUnit.SECONDS)).isTrue();
            releaseFirstOpen.countDown();
            firstStart.get(5, TimeUnit.SECONDS);
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            releaseFirstOpen.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(client.openCount).isEqualTo(1);
        assertThat(client.closeCount).isEqualTo(1);
        assertThat(manager.isReady()).isFalse();
    }

    private static SshTunnelProperties validProperties() {
        return new SshTunnelProperties(
            true,
            "ssh.example.test",
            22,
            "tunnel-user",
            "tunnel-password",
            "127.0.0.1",
            15432,
            "database.internal",
            5432,
            Path.of("/tmp/known_hosts"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(5)
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while coordinating test", exception);
        }
    }

    private static final class FakeSshPortForwardingClient implements SshPortForwardingClient {

        private int openCount;
        private int closeCount;
        private boolean failOnOpen;
        private boolean openBeforeFailure;
        private boolean remainClosedAfterOpen;
        private boolean open;
        private RuntimeException openFailure;
        private RuntimeException isOpenFailure;
        private RuntimeException closeFailure;
        private int closeFailuresRemaining;
        private Runnable beforeOpen;

        @Override
        public void open(SshTunnelProperties properties) {
            openCount++;
            if (beforeOpen != null) {
                beforeOpen.run();
            }
            if (openBeforeFailure) {
                open = true;
            }
            if (openFailure != null) {
                throw openFailure;
            }
            if (failOnOpen) {
                throw new IllegalStateException("simulated open failure");
            }
            open = !remainClosedAfterOpen;
        }

        @Override
        public boolean isOpen() {
            if (isOpenFailure != null) {
                throw isOpenFailure;
            }
            return open;
        }

        @Override
        public void close() {
            closeCount++;
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining--;
                throw closeFailure;
            }
            open = false;
        }

        void closeExternally() {
            open = false;
        }
    }
}
