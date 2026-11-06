package com.adn.dabaeum.classification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.classification.domain.CourseClassification;
import com.adn.dabaeum.classification.domain.CourseClassificationRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 설계서 §12.3 — 강좌에 붙일 수 있는 코드: 존재·활성(조상 포함)·하위 없음. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseClassificationServiceTest {

    @Mock CourseClassificationRepository repository;
    private CourseClassificationService service;

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenReturn(List.of(
            node("TSIX_040000", 1, null, "문화예술교육", true),
            node("TSIX_040200", 2, "TSIX_040000", "생활문화예술", true),
            node("TSIX_040201", 3, "TSIX_040200", "생활공예", true),
            node("TSIX_040202", 3, "TSIX_040200", "사진강좌", false),
            node("TSIX_060000", 1, null, "시민참여교육", false),
            node("TSIX_060100", 2, "TSIX_060000", "시민책무성", true),
            node("TSIX_060101", 3, "TSIX_060100", "주민자치교육", true),
            node("TSIX_070000", 1, null, "성인진로개발역량향상교육", true)));
        service = new CourseClassificationService(repository);
    }

    @Test
    void leafThirdLevelCodeIsAssignable() {
        service.requireAssignable("TSIX_040201");
    }

    @Test
    void firstLevelWithoutChildrenIsAssignable() {
        service.requireAssignable("TSIX_070000");
    }

    @Test
    void codeWithChildrenIsRejected() {
        assertValidation(() -> service.requireAssignable("TSIX_040200"));
    }

    @Test
    void disabledCodeIsRejected() {
        assertValidation(() -> service.requireAssignable("TSIX_040202"));
    }

    @Test
    void codeUnderDisabledAncestorIsRejected() {
        assertValidation(() -> service.requireAssignable("TSIX_060101"));
    }

    @Test
    void unknownCodeIsRejected() {
        assertValidation(() -> service.requireAssignable("TSIX_999999"));
    }

    @Test
    void pathListsNamesFromTheTop() {
        assertThat(service.pathOf("TSIX_040201")).containsExactly("문화예술교육", "생활문화예술", "생활공예");
        assertThat(service.pathOf(null)).isEmpty();
        assertThat(service.pathOf("TSIX_999999")).isEmpty();
    }

    @Test
    void enablingUnknownCodeIsNotFound() {
        when(repository.updateEnabled("TSIX_999999", true)).thenReturn(false);

        assertThatThrownBy(() -> service.setEnabled("TSIX_999999", true))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.COURSE_CLASSIFICATION_NOT_FOUND));
    }

    private static CourseClassification node(String code, int level, String parent, String name, boolean enabled) {
        return new CourseClassification(code, level, parent, name, 1, enabled);
    }

    private static void assertValidation(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
            assertThat(exception.status().value()).isEqualTo(422);
        });
    }
}
