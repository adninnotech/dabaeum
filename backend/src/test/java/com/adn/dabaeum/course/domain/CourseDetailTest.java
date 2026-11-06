package com.adn.dabaeum.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseDetailTest {

    @Test
    void empty_는_무료에_수료인증_꺼진_기본값을_준다() {
        CourseDetail detail = CourseDetail.empty();

        assertThat(detail.tuitionType()).isEqualTo(CourseFeeType.FREE);
        assertThat(detail.materialFeeType()).isEqualTo(CourseFeeType.FREE);
        assertThat(detail.completionEnabled()).isFalse();
        assertThat(detail.issueCertificate()).isFalse();
        assertThat(detail.instructorProfileVisible()).isTrue();
        assertThat(detail.selectionMethod()).isEqualTo(CourseSelectionMethod.FCFS);
        assertThat(detail.targetAudiences()).isEmpty();
    }

    @Test
    void 수강대상은_순서에_무관하게_중복없이_보관된다() {
        CourseDetail detail = CourseDetail.empty().withTargetAudiences(
            List.of(CourseTargetAudience.ADULTS, CourseTargetAudience.ADULTS,
                CourseTargetAudience.YOUTH));

        assertThat(detail.targetAudiences())
            .containsExactlyInAnyOrder(
                CourseTargetAudience.ADULTS, CourseTargetAudience.YOUTH);
    }

    @Test
    void 유료인데_금액이_없으면_거부한다() {
        assertThatThrownBy(() -> CourseDetail.empty()
            .withTuition(CourseFeeType.PAID, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tuitionAmount");
    }

    @Test
    void 금액은_음수일_수_없다() {
        assertThatThrownBy(() -> CourseDetail.empty()
            .withTuition(CourseFeeType.PAID, new BigDecimal("-1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tuitionAmount");
    }

    @Test
    void 태그는_60자를_넘을_수_없다() {
        String tooLong = "가".repeat(61);

        assertThatThrownBy(() -> CourseDetail.empty().withTags(tooLong))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tags");
    }

    @Test
    void 필요_진도율은_0에서_100_사이여야_한다() {
        assertThatThrownBy(() -> CourseDetail.empty()
            .withCompletion(true, CourseCompletionCriteria.PROGRESS, 101, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requiredProgressRate");
    }

    @Test
    void 수강대상은_쉼표문자열로_직렬화되고_되살아난다() {
        CourseDetail detail = CourseDetail.empty().withTargetAudiences(
            List.of(CourseTargetAudience.ANYONE, CourseTargetAudience.MEN));

        String serialized = detail.targetAudiencesAsText();

        assertThat(CourseDetail.parseTargetAudiences(serialized))
            .containsExactlyInAnyOrderElementsOf(Set.of(
                CourseTargetAudience.ANYONE, CourseTargetAudience.MEN));
    }

    @Test
    void 빈_문자열이나_null_을_되살리면_빈_집합이다() {
        assertThat(CourseDetail.parseTargetAudiences(null)).isEmpty();
        assertThat(CourseDetail.parseTargetAudiences("")).isEmpty();
        assertThat(CourseDetail.parseTargetAudiences("  ")).isEmpty();
    }
}
