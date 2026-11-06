package com.adn.dabaeum.attendance.application.port;

import java.util.Optional;

/**
 * 다대구 모바일웹 로그인. 학생마다 init 을 따로 부르는 것이 1:N 의 핵심이다 —
 * 다대구가 그려 주는 QR 은 인증 1회 뒤 서버가 방을 파괴해 1:1 이다(2026-09-08 실측).
 *
 * <p>siteId 는 application 이 넘긴다(ArchUnit: infrastructure 는 application.port 밖을 모른다).
 */
public interface DadaeguMobileWebGateway {

    /** 학생 한 명분 세션을 연다. 실패하면 {@link DadaeguMobileWebUnavailableException}. */
    DadaeguMobileWebSession init(String siteId);

    /** 아직 학생이 인증하지 않았으면 empty. 값이 있으면 DID 암호문(RSA/ECB/PKCS1). */
    Optional<String> poll(String siteId, String sessionKey);
}
