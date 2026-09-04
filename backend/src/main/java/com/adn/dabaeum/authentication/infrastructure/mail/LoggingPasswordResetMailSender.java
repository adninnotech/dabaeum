package com.adn.dabaeum.authentication.infrastructure.mail;

import com.adn.dabaeum.authentication.application.port.PasswordResetMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SMTP 인프라가 준비되기 전까지 사용하는 로그 기반 스텁.
 * 토큰 원문을 로그에 남기지 않는다.
 */
@Component
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    private static final Logger log =
        LoggerFactory.getLogger(LoggingPasswordResetMailSender.class);

    @Override
    public void sendResetLink(String email, String rawToken) {
        log.info("Password reset link issued (mail delivery stub). tokenLength={}",
            rawToken == null ? 0 : rawToken.length());
    }
}
