package com.adn.dabaeum.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SanitizedMyBatisLogTest {

    @Test
    void redactsMyBatisParameterValuesButPreservesSqlMessage() {
        assertThat(SanitizedMyBatisLog.sanitize(
            "==> Preparing: SELECT * FROM users WHERE email = ?"
        )).isEqualTo(
            "==> Preparing: SELECT * FROM users WHERE email = ?"
        );
        assertThat(SanitizedMyBatisLog.sanitize(
            "==> Parameters: person@example.com(String), secret-token(String)"
        )).isEqualTo("==> Parameters: ***MASKED***");
    }
}
