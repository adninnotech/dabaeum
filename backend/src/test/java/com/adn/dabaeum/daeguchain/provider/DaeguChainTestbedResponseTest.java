package com.adn.dabaeum.daeguchain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainReceipt;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import com.adn.dabaeum.daeguchain.config.DaeguChainProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 2026-09-11 대구체인 테스트베드 응답 원문({@code src/test/resources/daeguchain/testbed-2026-09-11})으로
 * Provider 를 검사한다. 실제 대구체인은 업무 오류를 HTTP 406 + state=OOPS + rcode.bcode 로 주고
 * (에뮬레이터가 문서만 보고 정했던 200 + ERROR + rcode.code 와 다름), 정상 경로는 가정과 같았다 (ADR-0005 §8.1).
 */
@ExtendWith(OutputCaptureExtension.class)
class DaeguChainTestbedResponseTest {

    private static final String STORAGE = "http://testbed.test" + DaeguChainStorageClient.STORAGE_PREFIX;
    private static final String KEY = "TB1DFHZXO2T2DZKR";
    private static final String HASH = "b35f1ce90c3ca4c33041471a5ea480996bd4ca6071da656748298d59d04773b2";
    /** 실측 토큰 오류 메시지에 그대로 되돌아온 값. 설정 토큰으로 쓰면 마스킹을 검사할 수 있다. */
    private static final String ECHOED_TOKEN = "wrong-token-TBNAZ83W70WTC8R0";
    private static final CredentialRegistryReference REFERENCE =
        new CredentialRegistryReference(BlockchainProvider.DAEGUCHAIN, KEY, null);
    private static final Instant EVENT_TIME = Instant.ofEpochMilli(1789091633199L);

    private MockRestServiceServer server;
    private DaeguChainStorageProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DaeguChainProperties properties = new DaeguChainProperties("http://testbed.test", ECHOED_TOKEN,
            "dchain", "RRAVZRJUBJNRRTCFMPYW", null, Duration.ofSeconds(5), Duration.ofSeconds(30));
        provider = new DaeguChainStorageProvider(
            new DaeguChainStorageClient(builder.build(), new ObjectMapper(), properties), true);
    }

    @Test
    void mapsMeasuredBcodesToNeutralCodesAndMasksTheEchoedToken(CapturedOutput output) {
        // B0594 는 "Mitum 미정의 오류" 포괄 코드라 not found 문장이 있을 때만 NOT_FOUND 다.
        String otherB0594 = fixture("get_data-not-found.json")
            .replace("not found - storage data for key", "consensus stalled for key");
        server.expect(requestTo(STORAGE + "/get_data")).andRespond(error406(fixture("get_data-not-found.json")));
        server.expect(requestTo(STORAGE + "/get_data")).andRespond(error406(otherB0594));
        server.expect(requestTo(STORAGE + "/get_data")).andRespond(error406(fixture("get_data-invalid-token.json")));
        server.expect(requestTo(STORAGE + "/create_data")).andRespond(error406(fixture("create_data-duplicate-key.json")));

        assertRegistryError(() -> provider.getCredentialState(REFERENCE), "BLOCKCHAIN_NOT_FOUND");
        assertRegistryError(() -> provider.getCredentialState(REFERENCE), "BLOCKCHAIN_READ_FAILED");
        assertRegistryError(() -> provider.getCredentialState(REFERENCE), "BLOCKCHAIN_RESPONSE_INVALID");
        assertRegistryError(() -> provider.createCredentialState(create()), "BLOCKCHAIN_ALREADY_EXISTS");
        // 실제 대구체인은 토큰 오류 메시지에 받은 토큰을 되돌려 준다. 설정 토큰이 로그에 남으면 안 된다.
        assertThat(output).contains("path=/get_data").doesNotContain(ECHOED_TOKEN);
        server.verify();
    }

    @Test
    void parsesRealWriteReadAndHistoryResponses() {
        server.expect(requestTo(STORAGE + "/create_data")).andRespond(ok(fixture("create_data-ok.json")));
        server.expect(requestTo(STORAGE + "/get_data")).andRespond(ok(fixture("get_data-ok.json")));
        server.expect(requestTo(STORAGE + "/data_history")).andRespond(ok(fixture("data_history-newest-first.json")));

        BlockchainReceipt created = provider.createCredentialState(create());
        CredentialRegistryState state = provider.getCredentialState(REFERENCE);
        List<BlockchainHistoryEntry> oldestFirst =
            provider.getCredentialHistory(REFERENCE, new BlockchainHistoryQuery(10, 0, false));

        assertThat(created.transactionId()).isEqualTo("HuWiX45czTNQod1N74kT831ZZdpV4iiC4z9nbCZa2hnz");
        assertThat(created.blockHeight()).isEqualTo(17L);
        assertThat(created.confirmed()).isTrue();
        assertThat(created.confirmedAt()).isEqualTo(Instant.parse("2026-09-11T01:46:28.96Z"));
        assertThat(state.status()).isEqualTo(RegistryStatus.ACTIVE);
        assertThat(state.vcHash()).isEqualTo(HASH);
        assertThat(state.eventTime()).isEqualTo(EVENT_TIME);
        assertThat(oldestFirst).extracting(BlockchainHistoryEntry::transactionId).containsExactly(
            "HuWiX45czTNQod1N74kT831ZZdpV4iiC4z9nbCZa2hnz", "3agxJFm3epwLy2bP56RAVAcsfGiUkHvqW4DyRtrSHVwJ");
        // 블록 18 의 timestamp 가 블록 17 보다 이르다. 시각으로 순서를 판단하면 안 된다는 실측 근거다.
        assertThat(oldestFirst.get(1).timestamp()).isBefore(oldestFirst.get(0).timestamp());
        server.verify();
    }

    private static void assertRegistryError(Runnable call, String code) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(BlockchainRegistryException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private static CredentialRegistryCreate create() {
        return new CredentialRegistryCreate(
            REFERENCE, 1, RegistryStatus.ACTIVE, HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME, null);
    }

    private static ResponseCreator ok(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static ResponseCreator error406(String body) {
        return withStatus(HttpStatus.NOT_ACCEPTABLE).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static String fixture(String name) {
        try (InputStream in = DaeguChainTestbedResponseTest.class.getResourceAsStream(
            "/daeguchain/testbed-2026-09-11/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("fixture not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
