package com.adn.dabaeum.nft.domain;

/**
 * NFT 원장 호출 실패. 메시지에 토큰·개인키를 담지 않는다.
 *
 * <p>{@link #OUTCOME_UNKNOWN} 은 요청을 보낸 뒤 응답을 못 받은 경우다. 체인에 생겼는지 모르므로
 * 자동으로 다시 보내면 안 된다.
 */
public final class NftLedgerException extends RuntimeException {

    public static final String TRANSPORT = "TRANSPORT";
    public static final String OUTCOME_UNKNOWN = "OUTCOME_UNKNOWN";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String CONTRACT_NOT_FOUND = "CONTRACT_NOT_FOUND";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String MALFORMED_RESPONSE = "MALFORMED_RESPONSE";
    public static final String UNKNOWN = "UNKNOWN";

    private final String code;

    public NftLedgerException(String code, String message) {
        this(code, message, null);
    }

    public NftLedgerException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null || code.isBlank() ? UNKNOWN : code;
    }

    public String code() {
        return code;
    }

    /** 체인이 처리 전에 확실히 거절했다. 체인에 아무것도 생기지 않았다. */
    public boolean definiteRefusal() {
        return UNAUTHORIZED.equals(code) || INSUFFICIENT_BALANCE.equals(code)
            || CONTRACT_NOT_FOUND.equals(code) || PERMISSION_DENIED.equals(code);
    }

    /** 아무것도 보내지 못했을 때만 그대로 다시 보내도 안전하다. */
    public boolean retryable() {
        return TRANSPORT.equals(code);
    }
}
