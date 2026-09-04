package com.adn.dabaeum.authentication.application.port;

/**
 * 비밀번호 재설정 안내를 발송하는 포트.
 * 운영 환경에서는 SMTP 구현으로 대체한다.
 */
public interface PasswordResetMailSender {

    void sendResetLink(String email, String rawToken);
}
