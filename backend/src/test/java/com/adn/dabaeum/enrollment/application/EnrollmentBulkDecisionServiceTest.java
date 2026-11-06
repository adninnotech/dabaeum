package com.adn.dabaeum.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** 스펙 #49 목록 체크박스 일괄 승인·반려. */
@ExtendWith(MockitoExtension.class)
class EnrollmentBulkDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");
    private static final UUID COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_COURSE_ID =
        UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID INSTITUTION_ID =
        UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MANAGER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LEARNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ID_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID ID_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Mock EnrollmentApplicationService enrollmentService;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock CourseRepository courseRepository;
    @Mock EnrollmentDecisionAuthority decisionAuthority;

    private EnrollmentBulkDecisionService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentBulkDecisionService(
            enrollmentService, enrollmentRepository, courseRepository, decisionAuthority);
        // 사유 누락·상한 초과는 저장소를 보기 전에 끝나므로 공용 스텁은 lenient 로 둔다.
        lenient().when(courseRepository.findActiveById(COURSE_ID))
            .thenReturn(Optional.of(course(COURSE_ID)));
        lenient().when(courseRepository.findActiveById(OTHER_COURSE_ID))
            .thenReturn(Optional.of(course(OTHER_COURSE_ID)));
    }

    @Test
    void 여러_건을_한_번에_승인한다() {
        when(enrollmentRepository.findAllByIds(List.of(ID_A, ID_B, ID_C))).thenReturn(
            List.of(enrollment(ID_A, COURSE_ID), enrollment(ID_B, COURSE_ID),
                enrollment(ID_C, COURSE_ID)));

        BulkDecisionResult result = service.bulkDecide(new BulkDecisionCommand(
            List.of(ID_A, ID_B, ID_C), BulkDecisionCommand.Decision.APPROVE, null),
            managerContext());

        assertThat(result.succeeded()).containsExactlyInAnyOrder(ID_A, ID_B, ID_C);
        assertThat(result.failed()).isEmpty();
    }

    @Test
    void 일부가_실패해도_나머지는_처리된다() {
        when(enrollmentRepository.findAllByIds(List.of(ID_A, ID_B, ID_C))).thenReturn(
            List.of(enrollment(ID_A, COURSE_ID), enrollment(ID_B, COURSE_ID),
                enrollment(ID_C, COURSE_ID)));
        // ID_A·ID_C 는 같은 메서드를 다른 인수로 부르므로 strict 스텁이면 PotentialStubbingProblem 이 난다.
        lenient().when(enrollmentService.approve(eq(ID_B), any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.ENROLLMENT_STATUS_CONFLICT,
            "Enrollment status has changed"));

        BulkDecisionResult result = service.bulkDecide(new BulkDecisionCommand(
            List.of(ID_A, ID_B, ID_C), BulkDecisionCommand.Decision.APPROVE, null),
            managerContext());

        assertThat(result.succeeded()).containsExactlyInAnyOrder(ID_A, ID_C);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).enrollmentId()).isEqualTo(ID_B);
        assertThat(result.failed().get(0).reason())
            .contains("ENROLLMENT_STATUS_CONFLICT");
    }

    @Test
    void 없는_id_가_섞이면_통째로_404_다() {
        // 없는 id 는 부분 실패가 아니라 클라이언트 오류다 — 화면이 보낸 목록이 이미 낡았다.
        when(enrollmentRepository.findAllByIds(List.of(ID_A, ID_B)))
            .thenReturn(List.of(enrollment(ID_A, COURSE_ID)));

        assertThatThrownBy(() -> service.bulkDecide(new BulkDecisionCommand(
            List.of(ID_A, ID_B), BulkDecisionCommand.Decision.APPROVE, null), managerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND));
        verify(enrollmentService, never()).approve(any(), any());
    }

    @Test
    void 강좌가_지워진_건은_배치를_죽이지_않고_건별_실패로_떨어진다() {
        UUID goneCourseId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(enrollmentRepository.findAllByIds(List.of(ID_A, ID_B)))
            .thenReturn(List.of(enrollment(ID_A, COURSE_ID), enrollment(ID_B, goneCourseId)));
        when(courseRepository.findActiveById(goneCourseId)).thenReturn(Optional.empty());
        lenient().when(enrollmentService.approve(eq(ID_B), any())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND, "Course not found"));

        BulkDecisionResult result = service.bulkDecide(new BulkDecisionCommand(
            List.of(ID_A, ID_B), BulkDecisionCommand.Decision.APPROVE, null), managerContext());

        assertThat(result.succeeded()).containsExactly(ID_A);
        assertThat(result.failed()).extracting(BulkDecisionResult.Failure::enrollmentId)
            .containsExactly(ID_B);
        assertThat(result.failed().get(0).reason()).contains("COURSE_NOT_FOUND");
    }

    @Test
    void 같은_id_를_두_번_보내도_한_번만_처리한다() {
        when(enrollmentRepository.findAllByIds(List.of(ID_A)))
            .thenReturn(List.of(enrollment(ID_A, COURSE_ID)));

        BulkDecisionResult result = service.bulkDecide(new BulkDecisionCommand(
            List.of(ID_A, ID_A), BulkDecisionCommand.Decision.APPROVE, null), managerContext());

        assertThat(result.succeeded()).containsExactly(ID_A);
        verify(enrollmentService).approve(eq(ID_A), any());
    }

    @Test
    void 거절은_사유가_필요하다() {
        assertThatThrownBy(() -> service.bulkDecide(new BulkDecisionCommand(
            List.of(ID_A), BulkDecisionCommand.Decision.REJECT, null), managerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> {
                assertThat(((ApiException) exception).status())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                assertThat(((ApiException) exception).details()).containsExactly("reason");
            });
    }

    @Test
    void 다른_기관_건이_섞이면_통째로_거부한다() {
        when(enrollmentRepository.findAllByIds(List.of(ID_A, ID_B))).thenReturn(
            List.of(enrollment(ID_A, COURSE_ID), enrollment(ID_B, OTHER_COURSE_ID)));
        // COURSE_ID 건이 먼저 같은 메서드를 통과하므로 strict 스텁이면 인수 불일치로 죽는다.
        lenient().doThrow(new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
            "Institution scope is not allowed"))
            .when(decisionAuthority).require(any(), eq(course(OTHER_COURSE_ID)));

        assertThatThrownBy(() -> service.bulkDecide(new BulkDecisionCommand(
            List.of(ID_A, ID_B), BulkDecisionCommand.Decision.APPROVE, null), managerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).status())
                .isEqualTo(HttpStatus.FORBIDDEN));

        verify(enrollmentService, never()).approve(any(), any());
    }

    @Test
    void 한_번에_100건을_넘으면_거부한다() {
        List<UUID> tooMany = IntStream.range(0, 101)
            .mapToObj(index -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> service.bulkDecide(new BulkDecisionCommand(
            tooMany, BulkDecisionCommand.Decision.APPROVE, null), managerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).details())
                .containsExactly("enrollmentIds"));

        verify(enrollmentRepository, never()).findAllByIds(any());
    }

    private Enrollment enrollment(UUID id, UUID courseId) {
        return new Enrollment(id, courseId, LEARNER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPLIED, NOW,
            null, null, null, null, null, null, NOW, NOW);
    }

    private Course course(UUID id) {
        return new Course(id, INSTITUTION_ID, "COURSE-1", "과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            null, null, 10, null, null, false, (BigDecimal) null,
            CourseStatus.RECRUITING, NOW, NOW, null);
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(MANAGER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
