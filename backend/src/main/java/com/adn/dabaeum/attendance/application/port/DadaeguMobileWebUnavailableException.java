package com.adn.dabaeum.attendance.application.port;

/** 다대구 서버에 닿지 못했거나 응답이 규격에 맞지 않는다. 우리 잘못이 아니므로 503 으로 옮긴다. */
public class DadaeguMobileWebUnavailableException extends RuntimeException {

    public DadaeguMobileWebUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
