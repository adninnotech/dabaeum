package com.adn.dabaeum.daeguchain.client;

/**
 * 대구체인 응답 봉투가 {@code state != OK} 이거나 전송 자체가 실패했을 때.
 * {@code code} 는 봉투의 {@code rcode.code} 이며, 전송 실패는 {@link #TRANSPORT} 다.
 * 메시지에 요청 본문(token 포함)을 담지 않는다.
 */
public final class DaeguChainApiException extends RuntimeException {

    public static final String TRANSPORT = "TRANSPORT";

    private final String code;
    private final int httpStatus;

    public DaeguChainApiException(String code, int httpStatus, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "UNKNOWN" : code;
        this.httpStatus = httpStatus;
    }

    public DaeguChainApiException(String code, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null || code.isBlank() ? "UNKNOWN" : code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
