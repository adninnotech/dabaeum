package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import org.junit.jupiter.api.Test;

/** 설계서 §12.3 — 목록 필터는 어느 단계 코드든 하위 포함(자릿수 기반 접두사). */
class CourseClassificationPrefixTest {

    @Test
    void stripsTrailingZeroPairsPerLevel() {
        assertThat(DefaultCourseApplicationService.classificationPrefix("TSIX_040000")).isEqualTo("TSIX_04");
        assertThat(DefaultCourseApplicationService.classificationPrefix("TSIX_040200")).isEqualTo("TSIX_0402");
        assertThat(DefaultCourseApplicationService.classificationPrefix("TSIX_040201")).isEqualTo("TSIX_040201");
    }

    @Test
    void blankMeansNoFilter() {
        assertThat(DefaultCourseApplicationService.classificationPrefix(null)).isNull();
        assertThat(DefaultCourseApplicationService.classificationPrefix(" ")).isNull();
    }

    @Test
    void malformedCodeIsBadRequest() {
        assertThatThrownBy(() -> DefaultCourseApplicationService.classificationPrefix("문화예술"))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST));
    }
}
