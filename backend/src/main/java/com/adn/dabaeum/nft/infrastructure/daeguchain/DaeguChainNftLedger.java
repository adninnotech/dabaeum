package com.adn.dabaeum.nft.infrastructure.daeguchain;

import com.adn.dabaeum.nft.domain.NftAmount;
import com.adn.dabaeum.nft.domain.NftLedgerException;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.domain.NftMintRequest;
import com.adn.dabaeum.nft.domain.NftMintSubmission;
import com.adn.dabaeum.nft.domain.NftToken;
import com.adn.dabaeum.nft.domain.NftTransactionState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 대구체인 NFT 721 API(https://www.daegu.go.kr/daeguchain/api-reference/api/nft-721) 구현.
 *
 * <p>응답은 트리로 읽는다. 봉투 안의 {@code response}·{@code receipt} 는 필드가 많고 체인이 늘릴 수 있다.
 * 발급 지갑 개인키는 mint 요청 본문에만 넣고, 오류 메시지·로그에 되돌아오면 가린다.
 */
public final class DaeguChainNftLedger implements NftLedgerPort {

    static final String PREFIX = "/daeguchain/v2/mitum";
    private static final Logger LOGGER = LoggerFactory.getLogger(DaeguChainNftLedger.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DaeguChainNftProperties properties;
    private final DaeguChainNftErrors errors;

    public DaeguChainNftLedger(RestClient restClient, ObjectMapper objectMapper, DaeguChainNftProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.errors = new DaeguChainNftErrors(properties.token(), properties.ownerPrivateKey());
    }

    /**
     * 요청을 보낸 뒤에는 "체인이 확실히 거절했다"(토큰·잔액·컨트랙트·권한 오류)가 아닌 모든 실패를
     * {@link NftLedgerException#OUTCOME_UNKNOWN} 으로 돌린다. fact hash 를 받은 뒤의 해석 실패는 대기로 둔다.
     */
    @Override
    public NftMintSubmission mint(NftMintRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("cont_addr", request.contractAddress());
        fields.put("owner_addr", properties.ownerAddress());
        fields.put("owner_pkey", properties.ownerPrivateKey());
        fields.put("receiver", request.receiver());
        fields.put("uri", request.uri());
        fields.put("hash", request.hash());
        fields.put("creator", request.creator());
        JsonNode envelope;
        try {
            envelope = send("/nft/mint", fields, true);
        } catch (NftLedgerException exception) {
            throw outcomeUnknownUnlessDefinite(exception);
        }
        Optional<String> pendingHash = errors.operationTimeoutHash(envelope);
        if (pendingHash.isPresent()) {
            return new NftMintSubmission(pendingHash.get(), NftTransactionState.pending());
        }
        JsonNode data;
        try {
            data = errors.requireOk("/nft/mint", envelope);
        } catch (NftLedgerException exception) {
            throw outcomeUnknownUnlessDefinite(exception);
        }
        String factHash = text(data.path("tx").path("fact_hash"));
        if (factHash == null) {
            throw new NftLedgerException(NftLedgerException.OUTCOME_UNKNOWN, "mint response has no fact_hash");
        }
        try {
            return new NftMintSubmission(factHash, receiptState(data.path("receipt")));
        } catch (RuntimeException exception) {
            LOGGER.warn("mint 영수증을 해석하지 못해 대기로 둔다: factHash={}", factHash);
            return new NftMintSubmission(factHash, NftTransactionState.pending());
        }
    }

    private static NftLedgerException outcomeUnknownUnlessDefinite(NftLedgerException exception) {
        if (exception.definiteRefusal() || exception.retryable()
            || NftLedgerException.OUTCOME_UNKNOWN.equals(exception.code())) {
            return exception;
        }
        return new NftLedgerException(NftLedgerException.OUTCOME_UNKNOWN, exception.getMessage(), exception);
    }

    @Override
    public NftTransactionState transaction(String factHash) {
        JsonNode envelope = send("/com/trx_info", Map.of("fact_hash", required(factHash, "factHash")), false);
        if (errors.notFound(envelope)) {
            return NftTransactionState.pending();
        }
        JsonNode info = errors.requireOk("/com/trx_info", envelope).path("trx_info");
        try {
            return receiptState(info);
        } catch (RuntimeException exception) {
            throw new NftLedgerException(NftLedgerException.MALFORMED_RESPONSE, "trx_info receipt is malformed");
        }
    }

    @Override
    public List<Long> mintedTokenIndexes(String contractAddress, String factHash) {
        JsonNode data = errors.requireOk("/nft/nft_idx", send("/nft/nft_idx", Map.of(
            "cont_addr", required(contractAddress, "contractAddress"),
            "fact_hash", required(factHash, "factHash")), false));
        List<Long> indexes = new ArrayList<>();
        for (JsonNode index : data.path("nft_idx")) {
            indexes.add(number(index, "nft_idx"));
        }
        return List.copyOf(indexes);
    }

    @Override
    public Optional<NftToken> token(String contractAddress, long index) {
        JsonNode envelope = send("/nft/token_info", Map.of(
            "cont_addr", required(contractAddress, "contractAddress"), "nft_idx", index), false);
        if (errors.notFound(envelope)) {
            return Optional.empty();
        }
        JsonNode info = errors.requireOk("/nft/token_info", envelope).path("info");
        return Optional.of(new NftToken(contractAddress, number(info.path("nft_idx"), "nft_idx"),
            text(info.path("owner")), text(info.path("uri")), text(info.path("hash")),
            info.path("active").asBoolean(false)));
    }

    @Override
    public long totalSupply(String contractAddress) {
        JsonNode data = errors.requireOk("/nft/tot_supply", send("/nft/tot_supply",
            Map.of("cont_addr", required(contractAddress, "contractAddress")), false));
        return number(data.path("supply"), "supply");
    }

    @Override
    public List<NftAmount> balance(String address) {
        JsonNode data = errors.requireOk("/com/acc_balance", send("/com/acc_balance",
            Map.of("address", required(address, "address")), false));
        List<NftAmount> amounts = new ArrayList<>();
        for (JsonNode amount : data.path("balance")) {
            amounts.add(new NftAmount(text(amount.path("amount")), text(amount.path("currency"))));
        }
        return List.copyOf(amounts);
    }

    private JsonNode send(String path, Map<String, ?> fields, boolean writes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", properties.token());
        body.put("chain", properties.chain());
        body.putAll(fields);
        String responseBody;
        try {
            responseBody = restClient.post()
                .uri(properties.baseUrl() + PREFIX + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                // 오류 상태도 봉투를 담고 있으므로 본문을 읽는다.
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .body(String.class);
        } catch (RestClientException exception) {
            throw errors.transport(path, writes, exception);
        }
        try {
            JsonNode envelope = objectMapper.readTree(responseBody == null ? "" : responseBody);
            if (envelope == null || !envelope.isObject()) {
                throw new NftLedgerException(NftLedgerException.MALFORMED_RESPONSE, path + " response is not an object");
            }
            return envelope;
        } catch (NftLedgerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("대구체인 NFT 응답이 JSON 이 아니다: path={}", path);
            throw new NftLedgerException(NftLedgerException.MALFORMED_RESPONSE, path + " response is not JSON");
        }
    }

    /** {@code in_state} 가 명시적으로 false 이거나 사유가 있을 때만 거절이다. 값이 없으면 아직 모른다. */
    private static NftTransactionState receiptState(JsonNode receipt) {
        if (receipt.isMissingNode() || receipt.isNull()) {
            return NftTransactionState.pending();
        }
        String reason = text(receipt.path("reason"));
        JsonNode inState = receipt.path("in_state");
        if (reason != null || (inState.isBoolean() && !inState.asBoolean())) {
            return NftTransactionState.rejected(reason);
        }
        if (!inState.isBoolean()) {
            return NftTransactionState.pending();
        }
        String confirmedAt = text(receipt.path("confirmed_at"));
        return NftTransactionState.confirmed(number(receipt.path("height"), "height"),
            confirmedAt == null ? null : Instant.parse(confirmedAt));
    }

    /** 0 이상의 정수만 받는다. 문자열 숫자도 받는다. */
    private static long number(JsonNode node, String name) {
        long value;
        try {
            if (node.isNumber()) {
                if (!node.isIntegralNumber()) {
                    throw new NumberFormatException("not integral");
                }
                value = node.asLong();
            } else {
                value = Long.parseLong(node.asString().trim());
            }
        } catch (RuntimeException exception) {
            throw new NftLedgerException(NftLedgerException.MALFORMED_RESPONSE, name + " is not a number");
        }
        if (value < 0) {
            throw new NftLedgerException(NftLedgerException.MALFORMED_RESPONSE, name + " is negative");
        }
        return value;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value == null || value.isBlank() ? null : value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
