package com.adn.dabaeum.nft.infrastructure.daeguchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.adn.dabaeum.nft.domain.NftAmount;
import com.adn.dabaeum.nft.domain.NftLedgerException;
import com.adn.dabaeum.nft.domain.NftMintRequest;
import com.adn.dabaeum.nft.domain.NftMintSubmission;
import com.adn.dabaeum.nft.domain.NftToken;
import com.adn.dabaeum.nft.domain.NftTransactionState;
import com.adn.dabaeum.nft.domain.NftTransactionStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class DaeguChainNftLedgerTest {

    private static final String BASE = "https://chain.test/daeguchain/v2/mitum";
    private static final String TOKEN = "secret-app-token";
    private static final String OWNER = "0x35Fa3efFe13dc46FC1e1419A8E64673874c811Aafca";
    private static final String OWNER_KEY = "owner-private-key-fpr";
    private static final String CONTRACT = "0x17f19e62AEA8980BD024b1917D0b6A19359516b6fca";
    private static final String FACT = "G3vcNhTzHwBa6iKhZ2ucPkocdQsGuaJQH9LuGCaSJqK6";
    private static final String HASH = "a0b4d697376196eae96f1e82c2ac32072894a942b0ea2bf85671f4bc29571f37";
    private static final String URI = "https://dabaeum.example/api/v1/public/nft/badges/1";

    private MockRestServiceServer server;
    private DaeguChainNftLedger ledger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ledger = new DaeguChainNftLedger(builder.build(), new ObjectMapper(), new DaeguChainNftProperties(
            "https://chain.test", TOKEN, "dchain", OWNER, OWNER_KEY, Duration.ofSeconds(40)));
    }

    @Test
    void mintSignsWithTheOwnerWalletAndReturnsTheConfirmedReceipt() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.token").value(TOKEN))
            .andExpect(jsonPath("$.chain").value("dchain"))
            .andExpect(jsonPath("$.cont_addr").value(CONTRACT))
            .andExpect(jsonPath("$.owner_addr").value(OWNER))
            .andExpect(jsonPath("$.owner_pkey").value(OWNER_KEY))
            .andExpect(jsonPath("$.receiver").value(OWNER))
            .andExpect(jsonPath("$.uri").value(URI))
            .andExpect(jsonPath("$.hash").value(HASH))
            .andExpect(jsonPath("$.creator").value(OWNER))
            .andRespond(withSuccess(mintResponse(true, ""), MediaType.APPLICATION_JSON));

        NftMintSubmission submission = ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER));

        server.verify();
        assertThat(submission.factHash()).isEqualTo(FACT);
        assertThat(submission.state().status()).isEqualTo(NftTransactionStatus.CONFIRMED);
        assertThat(submission.state().height()).isEqualTo(56349L);
        assertThat(submission.state().confirmedAt()).isEqualTo(Instant.parse("2024-08-16T08:35:50.123Z"));
    }

    @Test
    void mintReceiptWithAReasonIsRejected() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess(mintResponse(false, "not enough balance"), MediaType.APPLICATION_JSON));

        NftMintSubmission submission = ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER));

        assertThat(submission.state().status()).isEqualTo(NftTransactionStatus.REJECTED);
        assertThat(submission.state().reason()).isEqualTo("not enough balance");
    }

    @Test
    void operationTimeoutKeepsTheFactHashFromTheMessageAndStaysPending() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess(sample("operation-timeout.json"), MediaType.APPLICATION_JSON));

        NftMintSubmission submission = ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER));

        assertThat(submission.factHash()).isEqualTo(FACT);
        assertThat(submission.state().status()).isEqualTo(NftTransactionStatus.PENDING);
    }

    @Test
    void readTimeoutAfterSendingMeansTheOutcomeIsUnknown() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER)))
            .isInstanceOfSatisfying(NftLedgerException.class, exception -> {
                assertThat(exception.code()).isEqualTo(NftLedgerException.OUTCOME_UNKNOWN);
                assertThat(exception.retryable()).isFalse();
            });
    }

    @Test
    void connectFailureMeansNothingWasSentAndCanBeRetried() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER)))
            .isInstanceOfSatisfying(NftLedgerException.class, exception -> {
                assertThat(exception.code()).isEqualTo(NftLedgerException.TRANSPORT);
                assertThat(exception.retryable()).isTrue();
            });
    }

    @Test
    void insufficientBalanceIsAKnownRefusal(CapturedOutput output) {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess(sample("insufficient-balance.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER)))
            .isInstanceOfSatisfying(NftLedgerException.class, exception -> {
                assertThat(exception.code()).isEqualTo(NftLedgerException.INSUFFICIENT_BALANCE);
                assertThat(exception.retryable()).isFalse();
            });
        assertThat(output.getAll()).doesNotContain(OWNER_KEY).doesNotContain(TOKEN);
    }

    @Test
    void secretsEchoedBackInAnErrorMessageAreMasked(CapturedOutput output) {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess("""
                {"state":"OOPS","rcode":{"bcode":"B0701"},"msg":"유효하지 않은 사용자 토큰입니다. (%s) key=%s","data":""}
                """.formatted(TOKEN, OWNER_KEY), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER)))
            .isInstanceOfSatisfying(NftLedgerException.class, exception -> {
                assertThat(exception.code()).isEqualTo(NftLedgerException.UNAUTHORIZED);
                assertThat(exception.getMessage()).doesNotContain(TOKEN).doesNotContain(OWNER_KEY);
            });
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain(OWNER_KEY);
    }

    @Test
    void transactionReadsTheRealConfirmedResponse() {
        server.expect(requestTo(BASE + "/com/trx_info"))
            .andExpect(jsonPath("$.fact_hash").value(FACT))
            .andExpect(jsonPath("$.owner_pkey").doesNotExist())
            .andRespond(withSuccess(sample("trx-info-confirmed.json"), MediaType.APPLICATION_JSON));

        NftTransactionState state = ledger.transaction(FACT);

        assertThat(state.status()).isEqualTo(NftTransactionStatus.CONFIRMED);
        assertThat(state.height()).isEqualTo(329094L);
        assertThat(state.confirmedAt()).isEqualTo(Instant.parse("2026-10-27T11:24:51.696Z"));
    }

    @Test
    void transactionNotYetOnChainIsPending() {
        server.expect(requestTo(BASE + "/com/trx_info"))
            .andRespond(withSuccess(sample("trx-info-not-found.json"), MediaType.APPLICATION_JSON));

        assertThat(ledger.transaction(FACT).status()).isEqualTo(NftTransactionStatus.PENDING);
    }

    @Test
    void mintedTokenIndexesComeFromTheFactHash() {
        server.expect(requestTo(BASE + "/nft/nft_idx"))
            .andExpect(jsonPath("$.cont_addr").value(CONTRACT))
            .andExpect(jsonPath("$.fact_hash").value(FACT))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"cont_addr":"%s","fact_hash":"%s","nft_idx":[75]}}
                """.formatted(CONTRACT, FACT), MediaType.APPLICATION_JSON));

        assertThat(ledger.mintedTokenIndexes(CONTRACT, FACT)).containsExactly(75L);
    }

    @Test
    void tokenInfoMapsOwnerUriHashAndActive() {
        server.expect(requestTo(BASE + "/nft/token_info"))
            .andExpect(jsonPath("$.nft_idx").value(75))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"cont_addr":"%s","info":{"_hint":"mitum-nft-nft-v0.0.1",
                 "nft_idx":75,"active":true,"owner":"%s","hash":"%s","uri":"%s","approved":"",
                 "creators":{"signers":[{"account":"%s","share":100,"signed":false}]}}}}
                """.formatted(CONTRACT, OWNER, HASH, URI, OWNER), MediaType.APPLICATION_JSON));

        Optional<NftToken> token = ledger.token(CONTRACT, 75);

        assertThat(token).contains(new NftToken(CONTRACT, 75, OWNER, URI, HASH, true));
    }

    @Test
    void totalSupplyAndBalanceAreRead() {
        server.expect(requestTo(BASE + "/nft/tot_supply"))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"cont_addr":"%s","supply":"12"}}
                """.formatted(CONTRACT), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/com/acc_balance"))
            .andExpect(jsonPath("$.address").value(OWNER))
            .andRespond(withSuccess(sample("balance.json"), MediaType.APPLICATION_JSON));

        long supply = ledger.totalSupply(CONTRACT);
        List<NftAmount> balance = ledger.balance(OWNER);

        assertThat(supply).isEqualTo(12L);
        assertThat(balance).containsExactly(new NftAmount("20000000000", "DMC"));
    }

    @Test
    void mintRequestAcceptsOnlyHttpsAddressesOrDabaeumIdentifiers() {
        new NftMintRequest(CONTRACT, OWNER, "urn:dabaeum:nft:badges:" + java.util.UUID.randomUUID(), HASH, OWNER);
        assertThatThrownBy(() -> new NftMintRequest(CONTRACT, OWNER, "http://118.45.182.100:8088/x", HASH, OWNER))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NftMintRequest(CONTRACT, OWNER, "urn:other:x", HASH, OWNER))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mintRequestRejectsAHashThatIsNotSha256Hex() {
        assertThatThrownBy(() -> new NftMintRequest(CONTRACT, OWNER, URI, "not-a-hash", OWNER))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receiptWithoutInStateIsPendingNotRejected() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"tx":{"fact_hash":"%s"},
                 "receipt":{"height":56349,"confirmed_at":"2024-08-16T08:35:50.123Z"}}}
                """.formatted(FACT), MediaType.APPLICATION_JSON));

        NftMintSubmission submission = ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER));

        assertThat(submission.factHash()).isEqualTo(FACT);
        assertThat(submission.state().status()).isEqualTo(NftTransactionStatus.PENDING);
    }

    @Test
    void unparseableReceiptKeepsTheFactHashAsPending() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"tx":{"fact_hash":"%s"},
                 "receipt":{"height":"abc","confirmed_at":"yesterday","in_state":true,"reason":""}}}
                """.formatted(FACT), MediaType.APPLICATION_JSON));

        NftMintSubmission submission = ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER));

        assertThat(submission.factHash()).isEqualTo(FACT);
        assertThat(submission.state().status()).isEqualTo(NftTransactionStatus.PENDING);
    }

    @Test
    void gatewayErrorPageAfterSendingIsOutcomeUnknown() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                .body("<html>502 Bad Gateway</html>"));

        assertMintOutcomeUnknown();
    }

    @Test
    void okEnvelopeWithoutFactHashIsOutcomeUnknown() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"tx":{}}}
                """, MediaType.APPLICATION_JSON));

        assertMintOutcomeUnknown();
    }

    @Test
    void unmappedErrorCodeOnMintIsOutcomeUnknown() {
        server.expect(requestTo(BASE + "/nft/mint"))
            .andRespond(withSuccess("""
                {"state":"OOPS","rcode":{"bcode":"B0999"},"msg":"알 수 없는 오류","data":""}
                """, MediaType.APPLICATION_JSON));

        assertMintOutcomeUnknown();
    }

    @Test
    void contractNotFoundIsNotMistakenForAMissingToken() {
        server.expect(requestTo(BASE + "/nft/token_info"))
            .andRespond(withSuccess("""
                {"state":"OOPS","rcode":{"bcode":"B0703"},"msg":"컨트랙트를 찾을 수 없습니다.","data":""}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ledger.token(CONTRACT, 1))
            .isInstanceOfSatisfying(NftLedgerException.class,
                exception -> assertThat(exception.code()).isEqualTo(NftLedgerException.CONTRACT_NOT_FOUND));
    }

    @Test
    void negativeOrFractionalNumbersAreMalformed() {
        server.expect(requestTo(BASE + "/nft/tot_supply"))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"supply":-1}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/nft/nft_idx"))
            .andRespond(withSuccess("""
                {"state":"OK","rcode":{},"msg":"","data":{"nft_idx":[1.5]}}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ledger.totalSupply(CONTRACT))
            .isInstanceOfSatisfying(NftLedgerException.class,
                exception -> assertThat(exception.code()).isEqualTo(NftLedgerException.MALFORMED_RESPONSE));
        assertThatThrownBy(() -> ledger.mintedTokenIndexes(CONTRACT, FACT))
            .isInstanceOfSatisfying(NftLedgerException.class,
                exception -> assertThat(exception.code()).isEqualTo(NftLedgerException.MALFORMED_RESPONSE));
    }

    private void assertMintOutcomeUnknown() {
        assertThatThrownBy(() -> ledger.mint(new NftMintRequest(CONTRACT, OWNER, URI, HASH, OWNER)))
            .isInstanceOfSatisfying(NftLedgerException.class, exception -> {
                assertThat(exception.code()).isEqualTo(NftLedgerException.OUTCOME_UNKNOWN);
                assertThat(exception.retryable()).isFalse();
            });
    }

    private static String mintResponse(boolean inState, String reason) {
        return """
            {"state":"OK","rcode":{},"msg":"","data":{"contract":"%s",
             "data":{"receiver":"%s","uri":"%s","hash":"%s","creator":"%s"},
             "tx":{"hash":"OPHASH","fact_hash":"%s"},"issued":"2024-08-16T08:35:46.065Z",
             "receipt":{"hash":"OPHASH","height":56349,"confirmed_at":"2024-08-16T08:35:50.123Z",
              "reason":"%s","in_state":%s,"index":0}}}
            """.formatted(CONTRACT, OWNER, URI, HASH, OWNER, FACT, reason, inState);
    }

    private static String sample(String name) {
        try (InputStream in = DaeguChainNftLedgerTest.class.getResourceAsStream(
            "/daeguchain/nft-2026-10-28/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
