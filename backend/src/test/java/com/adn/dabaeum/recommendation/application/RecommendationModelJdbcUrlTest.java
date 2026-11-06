package com.adn.dabaeum.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.recommendation.application.RecommendationModelScheduler.DatabaseTarget;
import org.junit.jupiter.api.Test;

/** spring.datasource.url 에서 Python 에 넘길 host/port/db 를 뽑는다 (§12.2). */
class RecommendationModelJdbcUrlTest {

    @Test
    void 호스트_포트_DB_를_뽑는다() {
        assertThat(DatabaseTarget.fromJdbcUrl("jdbc:postgresql://127.0.0.1:15432/dabaeum_dev"))
            .isEqualTo(new DatabaseTarget("127.0.0.1", "15432", "dabaeum_dev"));
    }

    @Test
    void 포트가_없으면_5432_다() {
        assertThat(DatabaseTarget.fromJdbcUrl("jdbc:postgresql://db.local/dabaeum_local"))
            .isEqualTo(new DatabaseTarget("db.local", "5432", "dabaeum_local"));
    }

    @Test
    void 쿼리_문자열은_버린다() {
        assertThat(DatabaseTarget.fromJdbcUrl("jdbc:postgresql://localhost:5433/dabaeum_dev?sslmode=disable&ApplicationName=x"))
            .isEqualTo(new DatabaseTarget("localhost", "5433", "dabaeum_dev"));
    }

    @Test
    void 형식이_다르면_거부하고_URL_을_메시지에_넣지_않는다() {
        assertThatThrownBy(() -> DatabaseTarget.fromJdbcUrl("jdbc:mysql://h:3306/db?password=secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("secret");
        assertThatThrownBy(() -> DatabaseTarget.fromJdbcUrl(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatabaseTarget.fromJdbcUrl("jdbc:postgresql://h:5432/"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
