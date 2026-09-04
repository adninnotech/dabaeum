package com.adn.dabaeum.daeguchain.provider;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainReceipt;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReissue;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.blockchain.domain.StorageValue;
import com.adn.dabaeum.daeguchain.client.DaeguChainApiException;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;

/**
 * 대구체인 Storage API 를 {@link BlockchainRegistryPort} 로 옮긴다.
 *
 * <p>신규 Storage 경로({@code DCSTORE:} 키, {@code StorageValue} 값)만 지원한다. 레거시
 * {@code CERT:} 경로와 원자적 재발급은 대구체인 API 에 대응이 없어 거부한다. 재발급은 워커가
 * 이미 두 호출(구 키 SUPERSEDED → 신 키 생성)로 처리한다.
 *
 * <p>영수증의 트랜잭션 ID 는 Mitum {@code fact_hash} 다. 이력 항목에도 fact_hash 만 있어서 발급
 * 영수증과 재조정 영수증이 같은 식별자를 쓰게 한다.
 */
public final class DaeguChainStorageProvider implements BlockchainRegistryPort {

    private static final BlockchainProvider PROVIDER = BlockchainProvider.DAEGUCHAIN;
    private static final int HISTORY_FETCH_LIMIT = 50;

    private final DaeguChainStorageClient client;
    private final boolean writeEnabled;

    public DaeguChainStorageProvider(DaeguChainStorageClient client, boolean writeEnabled) {
        this.client = Objects.requireNonNull(client, "client");
        this.writeEnabled = writeEnabled;
    }

    @Override
    public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
        Objects.requireNonNull(command, "command");
        CredentialRegistryReference reference = requireCurrent(command.reference());
        requireWriteAllowed();
        JsonNode data = call(() -> client.createData(
            reference.dataKey(),
            storageValue(command.schemaVersion(), command.status(), command.vcHash(), command.eventTime())
        ), "BLOCKCHAIN_SUBMIT_FAILED");
        return receipt(data);
    }

    @Override
    public CredentialRegistryState getCredentialState(CredentialRegistryReference reference) {
        CredentialRegistryReference current = requireCurrent(reference);
        JsonNode data = call(() -> client.getData(current.dataKey()), "BLOCKCHAIN_READ_FAILED");
        if (data.path("deleted").asBoolean(false)) {
            throw new BlockchainRegistryException("BLOCKCHAIN_NOT_FOUND");
        }
        return state(current, data.path("data_key"), data.path("data_value"));
    }

    @Override
    public BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) {
        Objects.requireNonNull(command, "command");
        CredentialRegistryReference reference = requireCurrent(command.reference());
        requireWriteAllowed();
        JsonNode data = call(() -> client.updateData(
            reference.dataKey(),
            storageValue(command.schemaVersion(), command.status(), command.vcHash(), command.eventTime())
        ), "BLOCKCHAIN_SUBMIT_FAILED");
        return receipt(data);
    }

    @Override
    public BlockchainReceipt reissueCredentialState(CredentialRegistryReissue command) {
        Objects.requireNonNull(command, "command");
        // 명령 자체가 레거시 참조만 허용하고, 레거시는 FABRIC_POC 전용이다.
        throw new BlockchainRegistryException("BLOCKCHAIN_UNSUPPORTED_REFERENCE");
    }

    @Override
    public List<BlockchainHistoryEntry> getCredentialHistory(
        CredentialRegistryReference reference,
        BlockchainHistoryQuery query
    ) {
        Objects.requireNonNull(query, "query");
        CredentialRegistryReference current = requireCurrent(reference);
        // 대구체인의 offset 은 블록 높이라 Port 의 인덱스 offset 과 다르다. 최신 우선으로 최대한
        // 받아온 뒤 Port 의미(오래된 순서 기준 reverse·offset·limit)를 여기서 적용한다.
        JsonNode entries = call(
            () -> client.dataHistory(current.dataKey(), HISTORY_FETCH_LIMIT, 0, true),
            "BLOCKCHAIN_READ_FAILED");
        if (!entries.isArray()) {
            throw new BlockchainRegistryException("BLOCKCHAIN_RESPONSE_INVALID");
        }
        List<BlockchainHistoryEntry> oldestFirst = new ArrayList<>(entries.size());
        try {
            for (JsonNode entry : entries) {
                boolean deleted = entry.path("deleted").asBoolean(false);
                JsonNode operation = entry.path("operation");
                String factHash = requiredText(operation, "fact_hash");
                Instant timestamp = Instant.parse(requiredText(operation, "timestamp"));
                CredentialRegistryState state = deleted
                    ? null : state(current, entry.path("data_key"), entry.path("data_value"));
                oldestFirst.add(new BlockchainHistoryEntry(factHash, state, timestamp, deleted));
            }
        } catch (RuntimeException exception) {
            throw responseInvalid(exception);
        }
        Collections.reverse(oldestFirst);
        if (query.reverse()) {
            Collections.reverse(oldestFirst);
        }
        int start = Math.min(query.offset(), oldestFirst.size());
        int end = Math.min(start + query.limit(), oldestFirst.size());
        return List.copyOf(oldestFirst.subList(start, end));
    }

    @Override
    public OptionalLong ledgerHeight() {
        return client.healthHeight();
    }

    private CredentialRegistryState state(
        CredentialRegistryReference reference,
        JsonNode dataKey,
        JsonNode dataValue
    ) {
        try {
            if (!reference.dataKey().equals(dataKey.asString())) {
                throw new IllegalArgumentException("Storage response key does not match request");
            }
            StorageValue value = StorageValue.decode(dataValue.asString());
            return new CredentialRegistryState(
                reference.chainKey(), value.schemaVersion(), value.status(), value.vcHash(),
                value.eventTime(), PROVIDER);
        } catch (RuntimeException exception) {
            throw responseInvalid(exception);
        }
    }

    /**
     * Create/Update Data 응답의 {@code tx}·{@code receipt} 를 영수증으로 옮긴다.
     * {@code in_state=false} 면 미확정이며 {@code reason} 을 결과 코드로 남긴다.
     */
    private BlockchainReceipt receipt(JsonNode data) {
        try {
            String factHash = requiredText(data.path("tx"), "fact_hash");
            JsonNode receipt = data.path("receipt");
            boolean confirmed = receipt.path("in_state").asBoolean(false);
            String reason = receipt.path("reason").asString();
            Long height = receipt.path("height").isNumber() ? receipt.path("height").asLong() : null;
            Instant confirmedAt = confirmed ? parseInstant(receipt.path("confirmed_at")) : null;
            String resultCode = confirmed ? "VALID" : (reason == null || reason.isBlank() ? "INVALID" : reason);
            return new BlockchainReceipt(PROVIDER, factHash, factHash, height, confirmedAt, confirmed, resultCode);
        } catch (RuntimeException exception) {
            throw responseInvalid(exception);
        }
    }

    private static Instant parseInstant(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || node.asString().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(node.asString());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value.asString();
    }

    private static String storageValue(
        int schemaVersion,
        RegistryStatus status,
        String vcHash,
        Instant eventTime
    ) {
        // 원장 값의 시각은 epoch milliseconds 하나뿐이므로 밀리초로 내림한다. Clock 은 마이크로초를 주는데
        // StorageValue 는 왕복이 정확히 일치하는 값만 받으므로 내리지 않으면 모든 발급이 실패한다.
        return new StorageValue(schemaVersion, status, vcHash, eventTime.truncatedTo(ChronoUnit.MILLIS))
            .encode();
    }

    private static CredentialRegistryReference requireCurrent(CredentialRegistryReference reference) {
        Objects.requireNonNull(reference, "reference");
        if (reference.provider() != PROVIDER || reference.isLegacy()) {
            throw new BlockchainRegistryException("BLOCKCHAIN_UNSUPPORTED_REFERENCE");
        }
        return reference;
    }

    private void requireWriteAllowed() {
        if (!writeEnabled) {
            throw new BlockchainRegistryException("BLOCKCHAIN_WRITE_NOT_APPROVED");
        }
    }

    private static JsonNode call(Supplier<JsonNode> action, String fallbackCode) {
        try {
            return action.get();
        } catch (DaeguChainApiException exception) {
            throw new BlockchainRegistryException(translate(exception.code(), fallbackCode));
        } catch (BlockchainRegistryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BlockchainRegistryException(fallbackCode);
        }
    }

    /** 대구체인(에뮬레이터) 오류 코드를 Provider 중립 코드로 옮긴다. */
    static String translate(String code, String fallbackCode) {
        return switch (code) {
            case "DATA_NOT_FOUND", "PROJECT_NOT_FOUND" -> "BLOCKCHAIN_NOT_FOUND";
            case "DATA_ALREADY_EXISTS" -> "BLOCKCHAIN_ALREADY_EXISTS";
            case "WRITE_NOT_APPROVED" -> "BLOCKCHAIN_WRITE_NOT_APPROVED";
            case "LEDGER_READ_FAILED" -> "BLOCKCHAIN_READ_FAILED";
            case "LEDGER_SUBMIT_FAILED" -> "BLOCKCHAIN_SUBMIT_FAILED";
            case "LEDGER_COMMIT_TIMEOUT" -> "BLOCKCHAIN_COMMIT_TIMEOUT";
            case "LEDGER_UNAVAILABLE", DaeguChainApiException.TRANSPORT -> "BLOCKCHAIN_CONNECTION_FAILED";
            case "UNAUTHORIZED", "INVALID_CHAIN", "VALIDATION_FAILED", "MALFORMED_RESPONSE" ->
                "BLOCKCHAIN_RESPONSE_INVALID";
            default -> fallbackCode;
        };
    }

    private static BlockchainRegistryException responseInvalid(RuntimeException exception) {
        return exception instanceof BlockchainRegistryException registryException
            ? registryException
            : new BlockchainRegistryException("BLOCKCHAIN_RESPONSE_INVALID");
    }
}
