package com.adn.dabaeum.nft.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.nft.application.NftIssuanceScheduler;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.infrastructure.daeguchain.DaeguChainNftLedger;
import com.adn.dabaeum.nft.infrastructure.fake.FakeNftLedger;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftSourceRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class NftConfigurationTest {

    private static final String[] REQUIRED = {
        "dabaeum.nft.enabled=true",
        "dabaeum.nft.start-at=2026-11-01T00:00:00+09:00",
        "dabaeum.nft.wallet-address=0xWALLETfca",
        "dabaeum.nft.badge-contract-address=0xBADGEfca",
        "dabaeum.nft.certificate-contract-address=0xCERTfca",
    };

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(NftConfiguration.class)
        .withBean(NftIssuanceRepository.class, () -> mock(NftIssuanceRepository.class))
        .withBean(NftSourceRepository.class, () -> mock(NftSourceRepository.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(Clock.class, Clock::systemUTC);

    @Test
    void disabledByDefaultCreatesNothing() {
        context.run(app -> {
            assertThat(app).doesNotHaveBean(NftIssuanceScheduler.class);
            assertThat(app).doesNotHaveBean(NftLedgerPort.class);
        });
    }

    @Test
    void enabledUsesTheMemoryLedgerUnlessDaeguchainIsChosen() {
        context.withPropertyValues(REQUIRED).run(app -> {
            assertThat(app).hasSingleBean(NftIssuanceScheduler.class);
            assertThat(app.getBean(NftLedgerPort.class)).isInstanceOf(FakeNftLedger.class);
        });
    }

    @Test
    void daeguchainLedgerNeedsThePrivateKeyAndToken() {
        context.withPropertyValues(REQUIRED).withPropertyValues("dabaeum.nft.ledger=daeguchain").run(app ->
            assertThat(app).hasFailed());

        context.withPropertyValues(REQUIRED).withPropertyValues("dabaeum.nft.ledger=daeguchain",
            "dabaeum.nft.wallet-private-key=secret-key", "dabaeum.nft.daeguchain.token=secret-token").run(app -> {
                assertThat(app.getBean(NftLedgerPort.class)).isInstanceOf(DaeguChainNftLedger.class);
                assertThat(app.getBean(NftProperties.class).toString())
                    .doesNotContain("secret-key").doesNotContain("secret-token");
            });
    }

    @Test
    void enabledWithoutContractsFailsFast() {
        context.withPropertyValues("dabaeum.nft.enabled=true", "dabaeum.nft.start-at=2026-11-01T00:00:00Z",
            "dabaeum.nft.wallet-address=0xWALLETfca").run(app -> assertThat(app).hasFailed());
    }

    @Test
    void urlModeNeedsAnHttpsDomainButUrnModeOnlyUsesTheBaseForImages() {
        context.withPropertyValues(REQUIRED)
            .withPropertyValues("dabaeum.nft.uri-mode=url", "dabaeum.nft.metadata-base-url=http://118.45.182.100:8088")
            .run(app -> assertThat(app).hasFailed());
        context.withPropertyValues(REQUIRED)
            .withPropertyValues("dabaeum.nft.metadata-base-url=http://118.45.182.100:8088")
            .run(app -> assertThat(app.getBean(NftProperties.class).uriMode()).isEqualTo(NftProperties.URN));
        context.withPropertyValues(REQUIRED).withPropertyValues("dabaeum.nft.uri-mode=ftp")
            .run(app -> assertThat(app).hasFailed());
    }
}
