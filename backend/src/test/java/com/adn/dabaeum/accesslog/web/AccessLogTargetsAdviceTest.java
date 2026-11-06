package com.adn.dabaeum.accesslog.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessLogTargetsAdviceTest {

    record Row(UUID userId, String name) {
    }

    record Page(List<Row> data, ApiMeta meta) {
    }

    @Test
    void readsFieldFromEveryRowOfAPage() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        CapturedTargets captured = AccessLogTargetsAdvice.extract(
            new Page(List.of(new Row(a, "가"), new Row(b, "나"), new Row(a, "다")), null), "userId");

        assertThat(captured.userIds()).containsExactly(a, b);
        assertThat(captured.count()).isEqualTo(3);
    }

    @Test
    void readsFieldFromSingleApiResponse() {
        UUID a = UUID.randomUUID();

        CapturedTargets captured = AccessLogTargetsAdvice.extract(new ApiResponse<>(new Row(a, "가"), null), "userId");

        assertThat(captured.userIds()).containsExactly(a);
        assertThat(captured.count()).isEqualTo(1);
    }

    @Test
    void blankFieldCountsRowsOnly() {
        CapturedTargets captured = AccessLogTargetsAdvice.extract(
            new Page(List.of(new Row(UUID.randomUUID(), "가")), null), "");

        assertThat(captured.userIds()).isEmpty();
        assertThat(captured.count()).isEqualTo(1);
    }

    @Test
    void nonRecordBodyCountsNothing() {
        CapturedTargets captured = AccessLogTargetsAdvice.extract("plain", "userId");

        assertThat(captured.userIds()).isEmpty();
        assertThat(captured.count()).isZero();
    }
}
