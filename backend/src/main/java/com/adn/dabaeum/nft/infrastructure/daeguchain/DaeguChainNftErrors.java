package com.adn.dabaeum.nft.infrastructure.daeguchain;

import com.adn.dabaeum.nft.domain.NftLedgerException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * 대구체인 응답 봉투의 오류 해석. 문서의 B 코드와 2026-10-23·27 실측 메시지를 근거로 한다.
 * 실제 대구체인은 오류 메시지에 받은 토큰을 되돌려 주므로 토큰·개인키를 가린 뒤 내보낸다.
 */
final class DaeguChainNftErrors {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaeguChainNftErrors.class);
    /** "… Timeout(20000) Hash(G3vc…)" — BaaS 가 처리 확인을 기다리다 포기했지만 요청은 체인에 들어갔다. */
    private static final Pattern TIMEOUT_HASH = Pattern.compile("Timeout\\(\\d+\\)\\s*Hash\\(([^)\\s]+)\\)");
    private static final String NOT_FOUND_MESSAGE = "찾을 수 없습니다";

    private final String token;
    private final String privateKey;

    DaeguChainNftErrors(String token, String privateKey) {
        this.token = token;
        this.privateKey = privateKey;
    }

    JsonNode requireOk(String path, JsonNode envelope) {
        if ("OK".equals(envelope.path("state").asString())) {
            return envelope.path("data");
        }
        String bcode = envelope.path("rcode").path("bcode").asString();
        String message = mask(envelope.path("msg").asString());
        LOGGER.warn("대구체인 NFT 오류 응답: path={} bcode={} msg={}", path, bcode, message);
        throw new NftLedgerException(code(bcode), message);
    }

    Optional<String> operationTimeoutHash(JsonNode envelope) {
        if ("OK".equals(envelope.path("state").asString())) {
            return Optional.empty();
        }
        Matcher matcher = TIMEOUT_HASH.matcher(envelope.path("msg").asString());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** 데이터 미존재. 토큰·잔액·컨트랙트·권한 같은 확정 오류 코드면 메시지가 같아도 미존재가 아니다. */
    boolean notFound(JsonNode envelope) {
        if ("OK".equals(envelope.path("state").asString())) {
            return false;
        }
        String bcode = envelope.path("rcode").path("bcode").asString();
        return NftLedgerException.UNKNOWN.equals(code(bcode))
            && envelope.path("msg").asString().contains(NOT_FOUND_MESSAGE);
    }

    /** 연결 자체가 안 됐으면 아무것도 보내지 않았다. 그 밖의 실패는 쓰기 요청이면 결과를 모른다. */
    NftLedgerException transport(String path, boolean writes, RestClientException exception) {
        LOGGER.warn("대구체인 NFT 호출 실패: path={} type={}", path, rootCause(exception).getClass().getName());
        boolean notSent = rootCause(exception) instanceof ConnectException
            || rootCause(exception) instanceof UnknownHostException
            || rootCause(exception) instanceof NoRouteToHostException;
        String code = writes && !notSent ? NftLedgerException.OUTCOME_UNKNOWN : NftLedgerException.TRANSPORT;
        return new NftLedgerException(code, path + " call failed");
    }

    private static String code(String bcode) {
        return switch (bcode == null ? "" : bcode) {
            case "B0701" -> NftLedgerException.UNAUTHORIZED;
            case "B0702" -> NftLedgerException.INSUFFICIENT_BALANCE;
            case "B0703" -> NftLedgerException.CONTRACT_NOT_FOUND;
            case "B0770" -> NftLedgerException.PERMISSION_DENIED;
            default -> NftLedgerException.UNKNOWN;
        };
    }

    private String mask(String message) {
        String masked = message == null ? "" : message;
        if (token != null && !token.isEmpty()) {
            masked = masked.replace(token, "****");
        }
        if (privateKey != null && !privateKey.isEmpty()) {
            masked = masked.replace(privateKey, "****");
        }
        return masked;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
