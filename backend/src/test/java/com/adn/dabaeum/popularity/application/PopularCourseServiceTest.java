package com.adn.dabaeum.popularity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.popularity.domain.PopularCourseScore;
import com.adn.dabaeum.popularity.domain.PopularityRepository;
import com.adn.dabaeum.popularity.domain.PopularitySegment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §8.3 — 세그먼트 선택과 비공개 강좌 거르기. */
@ExtendWith(MockitoExtension.class)
class PopularCourseServiceTest {

    @Mock PopularityRepository popularity;
    @Mock CourseRepository courses;
    private PopularCourseService service;

    @BeforeEach
    void setUp() {
        service = new PopularCourseService(popularity, courses, new PopularityProperties(null, null, null));
    }

    @Test
    void unknownBandOrLimitIsBadRequest() {
        assertBadRequest(() -> service.top("25", 10));
        assertBadRequest(() -> service.top(null, 51));
    }

    @Test
    void ageBandSegmentHidesCoursesThatBecameDraft() {
        UUID open = UUID.randomUUID();
        UUID draft = UUID.randomUUID();
        when(popularity.top(PopularitySegment.AGE_BAND, "20", 30, 10)).thenReturn(List.of(
            new PopularCourseScore(open, 2, 5, BigDecimal.valueOf(11)),
            new PopularCourseScore(draft, 1, 1, BigDecimal.valueOf(4))));
        CourseView recruiting = view(open, CourseStatus.RECRUITING);
        CourseView hidden = view(draft, CourseStatus.DRAFT);
        when(courses.findActiveViewsByIds(List.of(open, draft))).thenReturn(List.of(hidden, recruiting));

        List<PopularCourseService.PopularCourse> result = service.top("20", 10);

        assertThat(result).singleElement().satisfies(item -> assertThat(item.score().courseId()).isEqualTo(open));
        Mockito.verify(courses, Mockito.never()).findActiveViewById(Mockito.any());
    }

    @Test
    void coursesAreLoadedInOneQueryAndKeepScoreOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        when(popularity.top(PopularitySegment.ALL, "*", 30, 10)).thenReturn(List.of(
            new PopularCourseScore(first, 3, 9, BigDecimal.valueOf(20)),
            new PopularCourseScore(deleted, 2, 2, BigDecimal.valueOf(8)),
            new PopularCourseScore(second, 1, 1, BigDecimal.valueOf(4))));
        List<CourseView> loaded = List.of(view(second, CourseStatus.RECRUITING), view(first, CourseStatus.RECRUITING));
        when(courses.findActiveViewsByIds(List.of(first, deleted, second))).thenReturn(loaded);

        assertThat(service.top(null, 10)).extracting(item -> item.score().courseId())
            .containsExactly(first, second);
    }

    private static CourseView view(UUID id, CourseStatus status) {
        Course course = Mockito.mock(Course.class);
        Mockito.lenient().when(course.id()).thenReturn(id);
        Mockito.lenient().when(course.status()).thenReturn(status);
        return new CourseView(course, 0, "기관", "강사");
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST));
    }
}
