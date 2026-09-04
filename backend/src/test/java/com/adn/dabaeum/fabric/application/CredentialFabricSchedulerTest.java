package com.adn.dabaeum.fabric.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.fabric.config.FabricWorkerProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CredentialFabricSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private final CredentialFabricWorker worker = mock(CredentialFabricWorker.class);
    private final CredentialFabricReconciler reconciler = mock(CredentialFabricReconciler.class);
    private final CredentialFabricScheduler scheduler = new CredentialFabricScheduler(
        worker, reconciler, new FabricWorkerProperties(true, Duration.ofSeconds(5), 10),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void processesWorkerThenReconcilerWithConfiguredBatch() {
        scheduler.runOnce();

        InOrder order = inOrder(worker, reconciler);
        order.verify(worker).processBatch(10, NOW);
        order.verify(reconciler).reconcileBatch(10, NOW);
    }

    @Test
    void isolatesEachBoundarySoOneFailureDoesNotStopCurrentOrNextCycle() {
        when(worker.processBatch(10, NOW))
            .thenThrow(new IllegalStateException("sensitive-payload-must-not-be-logged"))
            .thenReturn(0);

        assertThatCode(scheduler::runOnce).doesNotThrowAnyException();
        assertThatCode(scheduler::runOnce).doesNotThrowAnyException();

        verify(worker, org.mockito.Mockito.times(2)).processBatch(10, NOW);
        verify(reconciler, org.mockito.Mockito.times(2)).reconcileBatch(10, NOW);
    }
}
