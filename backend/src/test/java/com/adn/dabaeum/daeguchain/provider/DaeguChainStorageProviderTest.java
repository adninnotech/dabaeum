package com.adn.dabaeum.daeguchain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
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
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import com.adn.dabaeum.daeguchain.config.DaeguChainProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class DaeguChainStorageProviderTest {

    private static final String BASE = "http://emulator.test/daeguchain/v2/mitum/storage";
    private static final String KEY = "K1K2K3K4K5K6K7K8";
    private static final String HASH = "9e7fe71979664e8eafe8db9fd24181cdb988e4534450c876706952b6c23f7a69";
    private static final CredentialRegistryReference REFERENCE =
        new CredentialRegistryReference(BlockchainProvider.DAEGUCHAIN, KEY, null);
    private static final Instant EVENT_TIME = Instant.parse("2026-09-01T14:32:31.593123Z");

    private MockRestServiceServer server;
    private DaeguChainStorageProvider provider;
    private DaeguChainStorageProvider readOnlyProvider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DaeguChainStorageClient client = new DaeguChainStorageClient(
            builder.build(), new ObjectMapper(), properties("secret-token"));
        provider = new DaeguChainStorageProvider(client, true);
        readOnlyProvider = new DaeguChainStorageProvider(client, false);
    }

    @Test
    void createSendsTheDocumentedBodyAndMapsTheReceiptFromTxAndReceiptBlocks() {
        server.expect(requestTo(BASE + "/create_data"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.token").value("secret-token"))
            .andExpect(jsonPath("$.chain").value("dchain"))
            .andExpect(jsonPath("$.project_id").value("EVDCFTOIGQNUVJZDSYAP"))
            .andExpect(jsonPath("$.data_key").value(KEY))
            // 마이크로초 시각은 밀리초로 내려서 기록한다 (StorageValue 규칙)
            .andExpect(jsonPath("$.data_value").value("1|A|" + HASH + "|1788273151593"))
            .andRespond(withSuccess(writeResponse("FACT-1", 25, true, ""), MediaType.APPLICATION_JSON));

        BlockchainReceipt receipt = provider.createCredentialState(new CredentialRegistryCreate(
            REFERENCE, 1, RegistryStatus.ACTIVE, HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME, null));

        server.verify();
        assertThat(receipt.provider()).isEqualTo(BlockchainProvider.DAEGUCHAIN);
        assertThat(receipt.transactionId()).isEqualTo("FACT-1");
        assertThat(receipt.factHash()).isEqualTo("FACT-1");
        assertThat(receipt.blockHeight()).isEqualTo(25L);
        assertThat(receipt.confirmed()).isTrue();
        assertThat(receipt.resultCode()).isEqualTo("VALID");
        assertThat(receipt.confirmedAt()).isEqualTo(Instant.parse("2026-09-03T08:02:07.195Z"));
    }

    @Test
    void unconfirmedReceiptCarriesTheLedgerReasonAndNoConfirmationTime() {
        server.expect(requestTo(BASE + "/update_data"))
            .andExpect(jsonPath("$.data_value").value("1|R|" + HASH + "|1788273151593"))
            .andRespond(withSuccess(writeResponse("FACT-2", 26, false, "MVCC_READ_CONFLICT"),
                MediaType.APPLICATION_JSON));

        BlockchainReceipt receipt = provider.updateCredentialState(new CredentialRegistryUpdate(
            REFERENCE, 1, RegistryStatus.REVOKED, HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME));

        assertThat(receipt.confirmed()).isFalse();
        assertThat(receipt.resultCode()).isEqualTo("MVCC_READ_CONFLICT");
        assertThat(receipt.confirmedAt()).isNull();
    }

    @Test
    void getMapsTheStorageValueIntoRegistryState() {
        server.expect(requestTo(BASE + "/get_data"))
            .andExpect(jsonPath("$.data_key").value(KEY))
            .andRespond(withSuccess(ok("""
                {"cont_addr":"0x5265fca","data_key":"%s","data_value":"1|A|%s|1788272457593",
                 "deleted":false,"operation":{"fact_hash":"FACT-1","timestamp":"2026-09-01T14:32:32Z","height":25}}
                """.formatted(KEY, HASH)), MediaType.APPLICATION_JSON));

        CredentialRegistryState state = provider.getCredentialState(REFERENCE);

        assertThat(state.chainKey()).isEqualTo("DCSTORE:" + KEY);
        assertThat(state.status()).isEqualTo(RegistryStatus.ACTIVE);
        assertThat(state.vcHash()).isEqualTo(HASH);
        assertThat(state.eventTime()).isEqualTo(Instant.ofEpochMilli(1788272457593L));
        assertThat(state.provider()).isEqualTo(BlockchainProvider.DAEGUCHAIN);
    }

    @Test
    void deletedAndMissingDataBothBecomeNotFound() {
        server.expect(requestTo(BASE + "/get_data")).andRespond(withSuccess(ok("""
            {"cont_addr":"0x","data_key":"%s","data_value":"","deleted":true,
             "operation":{"fact_hash":"F","timestamp":"2026-09-01T14:32:32Z","height":30}}
            """.formatted(KEY)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/get_data")).andRespond(withSuccess(
            error("DATA_NOT_FOUND", 200, "storage data does not exist"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getCredentialState(REFERENCE))
            .isInstanceOfSatisfying(BlockchainRegistryException.class,
                e -> assertThat(e.code()).isEqualTo("BLOCKCHAIN_NOT_FOUND"));
        assertThatThrownBy(() -> provider.getCredentialState(REFERENCE))
            .isInstanceOfSatisfying(BlockchainRegistryException.class,
                e -> assertThat(e.code()).isEqualTo("BLOCKCHAIN_NOT_FOUND"));
    }

    @Test
    void translatesEmulatorErrorCodesToTheProviderNeutralAllowlist() {
        record Case(String code, int http, String expected) { }
        List<Case> cases = List.of(
            new Case("DATA_ALREADY_EXISTS", 200, "BLOCKCHAIN_ALREADY_EXISTS"),
            new Case("WRITE_NOT_APPROVED", 403, "BLOCKCHAIN_WRITE_NOT_APPROVED"),
            new Case("LEDGER_SUBMIT_FAILED", 502, "BLOCKCHAIN_SUBMIT_FAILED"),
            new Case("LEDGER_COMMIT_TIMEOUT", 504, "BLOCKCHAIN_COMMIT_TIMEOUT"),
            new Case("LEDGER_UNAVAILABLE", 503, "BLOCKCHAIN_CONNECTION_FAILED"),
            new Case("UNAUTHORIZED", 401, "BLOCKCHAIN_RESPONSE_INVALID"),
            new Case("SOMETHING_NEW", 200, "BLOCKCHAIN_SUBMIT_FAILED"));
        for (Case c : cases) {
            server.expect(requestTo(BASE + "/create_data")).andRespond(
                withStatus(HttpStatus.valueOf(c.http()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error(c.code(), c.http(), "message")));
        }

        for (Case c : cases) {
            assertThatThrownBy(() -> provider.createCredentialState(create()))
                .as(c.code())
                .isInstanceOfSatisfying(BlockchainRegistryException.class,
                    e -> assertThat(e.code()).isEqualTo(c.expected()));
        }
        server.verify();
    }

    @Test
    void transportFailureBecomesConnectionFailedAndMalformedBodyBecomesResponseInvalid() {
        server.expect(requestTo(BASE + "/get_data")).andRespond(withException(new IOException("down")));
        server.expect(requestTo(BASE + "/get_data")).andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> provider.getCredentialState(REFERENCE))
            .isInstanceOfSatisfying(BlockchainRegistryException.class,
                e -> assertThat(e.code()).isEqualTo("BLOCKCHAIN_CONNECTION_FAILED"));
        assertThatThrownBy(() -> provider.getCredentialState(REFERENCE))
            .isInstanceOfSatisfying(BlockchainRegistryException.class,
                e -> assertThat(e.code()).isEqualTo("BLOCKCHAIN_RESPONSE_INVALID"));
    }

    @Test
    void historyFetchesNewestFirstFromTheChainAndAppliesPortOrderingOffsetAndLimit() {
        String body = ok("""
            [{"cont_addr":"0x","data_key":"%1$s","data_value":"","deleted":true,
              "operation":{"fact_hash":"F-3","timestamp":"2026-09-03T00:00:03Z","height":30}},
             {"cont_addr":"0x","data_key":"%1$s","data_value":"1|R|%2$s|1788272457593","deleted":false,
              "operation":{"fact_hash":"F-2","timestamp":"2026-09-03T00:00:02Z","height":28}},
             {"cont_addr":"0x","data_key":"%1$s","data_value":"1|A|%2$s|1788272457593","deleted":false,
              "operation":{"fact_hash":"F-1","timestamp":"2026-09-03T00:00:01Z","height":25}}]
            """.formatted(KEY, HASH));
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(BASE + "/data_history"))
                .andExpect(jsonPath("$.limit").value("50"))
                .andExpect(jsonPath("$.offset").value("0"))
                .andExpect(jsonPath("$.reverse").value("true"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }

        List<BlockchainHistoryEntry> oldestFirst = provider.getCredentialHistory(
            REFERENCE, new BlockchainHistoryQuery(10, 0, false));
        assertThat(oldestFirst).extracting(BlockchainHistoryEntry::transactionId)
            .containsExactly("F-1", "F-2", "F-3");
        assertThat(oldestFirst.get(0).state().status()).isEqualTo(RegistryStatus.ACTIVE);
        assertThat(oldestFirst.get(1).state().status()).isEqualTo(RegistryStatus.REVOKED);
        assertThat(oldestFirst.get(2).deleted()).isTrue();
        assertThat(oldestFirst.get(2).state()).isNull();

        List<BlockchainHistoryEntry> newestFirst = provider.getCredentialHistory(
            REFERENCE, new BlockchainHistoryQuery(10, 0, true));
        assertThat(newestFirst).extracting(BlockchainHistoryEntry::transactionId)
            .containsExactly("F-3", "F-2", "F-1");

        List<BlockchainHistoryEntry> paged = provider.getCredentialHistory(
            REFERENCE, new BlockchainHistoryQuery(10, 2, false));
        assertThat(paged).extracting(BlockchainHistoryEntry::transactionId).containsExactly("F-3");
    }

    @Test
    void writeGuardBlocksSubmitsBeforeAnyHttpCall() {
        assertThatThrownBy(() -> readOnlyProvider.createCredentialState(create()))
            .isInstanceOfSatisfying(BlockchainRegistryException.class,
                e -> assertThat(e.code()).isEqualTo("BLOCKCHAIN_WRITE_NOT_APPROVED"));
        server.verify();
    }

    @Test
    void rejectsLegacyAndForeignProviderReferencesWithoutCallingTheChain() {
        CredentialRegistryReference legacy =
            new CredentialRegistryReference(BlockchainProvider.FABRIC_POC, null, "CERT-1");
        CredentialRegistryReference fabric =
            new CredentialRegistryReference(BlockchainProvider.FABRIC_POC, KEY, null);

        for (CredentialRegistryReference reference : List.of(legacy, fabric)) {
            assertThatThrownBy(() -> provider.getCredentialState(reference))
                .isInstanceOfSatisfying(BlockchainRegistryException.class,
                    e -> assertThat(e.code()).isEqualTo("BLOCKCHAIN_UNSUPPORTED_REFERENCE"));
        }
        server.verify();
    }

    @Test
    void omitsTheTokenFieldWhenNoneIsConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer anonymous = MockRestServiceServer.bindTo(builder).build();
        DaeguChainStorageProvider noToken = new DaeguChainStorageProvider(
            new DaeguChainStorageClient(builder.build(), new ObjectMapper(), properties(null)), true);
        anonymous.expect(requestTo(BASE + "/get_data"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.chain").value("dchain"))
            .andRespond(withSuccess(ok("""
                {"cont_addr":"0x","data_key":"%s","data_value":"1|A|%s|1","deleted":false,
                 "operation":{"fact_hash":"F","timestamp":"2026-09-01T14:32:32Z","height":1}}
                """.formatted(KEY, HASH)), MediaType.APPLICATION_JSON));

        assertThat(noToken.getCredentialState(REFERENCE).status()).isEqualTo(RegistryStatus.ACTIVE);
        anonymous.verify();
    }

    @Test
    void ledgerHeightIsEmptyWithoutAHealthUrlAndReadFromItWhenConfigured() {
        assertThat(provider.ledgerHeight()).isEmpty();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer health = MockRestServiceServer.bindTo(builder).build();
        DaeguChainProperties withHealth = new DaeguChainProperties("http://emulator.test", null, "dchain",
            "EVDCFTOIGQNUVJZDSYAP", "http://emulator.test/health", Duration.ofSeconds(5), Duration.ofSeconds(30));
        DaeguChainStorageProvider monitored = new DaeguChainStorageProvider(
            new DaeguChainStorageClient(builder.build(), new ObjectMapper(), withHealth), true);
        health.expect(requestTo("http://emulator.test/health")).andRespond(withSuccess(
            "{\"status\":\"UP\",\"fabric\":{\"mode\":\"fabric\",\"height\":\"29\"}}", MediaType.APPLICATION_JSON));

        assertThat(monitored.ledgerHeight()).hasValue(29L);
    }

    private static CredentialRegistryCreate create() {
        return new CredentialRegistryCreate(
            REFERENCE, 1, RegistryStatus.ACTIVE, HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME, null);
    }

    private static DaeguChainProperties properties(String token) {
        return new DaeguChainProperties("http://emulator.test/", token, "dchain",
            "EVDCFTOIGQNUVJZDSYAP", null, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    private static String ok(String data) {
        return "{\"state\":\"OK\",\"rcode\":{},\"msg\":\"\",\"data\":" + data + ",\"cid\":\"c1d2\"}";
    }

    private static String error(String code, int http, String message) {
        return "{\"state\":\"ERROR\",\"rcode\":{\"code\":\"" + code + "\",\"http\":" + http
            + "},\"msg\":\"" + message + "\",\"data\":null,\"cid\":\"c1d2\"}";
    }

    /** 문서의 Create/Update Data 응답에서 Provider 가 읽는 부분만 담은 본문. */
    private static String writeResponse(String factHash, long height, boolean inState, String reason) {
        return ok("""
            {"contract":"0x5265fca","data":{"data_key":"%s","data_value":"v"},
             "tx":{"hash":"OP-%s","fact_hash":"%s"},"issued":"2026-09-03T08:02:07.100Z",
             "response":{"hash":"OP-%s","fact":{"hash":"%s","_hint":"mitum-storage-create-data-operation-fact-v0.0.1"},"signs":[],"_hint":"x"},
             "receipt":{"_hint":"mitum-currency-operation-value-v0.0.1","hash":"%s","operation":{},
                        "height":%d,"confirmed_at":"2026-09-03T08:02:07.195Z","reason":"%s","in_state":%s,"index":0}}
            """.formatted(KEY, factHash, factHash, factHash, factHash, factHash, height, reason, inState));
    }
}
