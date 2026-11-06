package com.adn.dabaeum.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseCancellationEnrollmentHandlerTest {

    private static final Instant APPLIED = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();

    @Mock EnrollmentRepository repository;

    @Test
    void 승인된_수강은_철회로_신청과_대기는_취소로_닫는다() {
        Enrollment approved = enrollment(EnrollmentStatus.APPROVED);
        Enrollment applied = enrollment(EnrollmentStatus.APPLIED);
        Enrollment waitlisted = enrollment(EnrollmentStatus.WAITLISTED);
        when(repository.findActiveByCourseId(COURSE_ID)).thenReturn(List.of(approved, applied, waitlisted));
        when(repository.hasCompletionDecision(any())).thenReturn(false);
        when(repository.updateState(any(), any())).thenReturn(true);

        new CourseCancellationEnrollmentHandler(repository).onCourseCancelled(COURSE_ID, INSTITUTION_ID, NOW);

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(repository).updateState(captor.capture(), eq(EnrollmentStatus.APPROVED));
        Enrollment withdrawn = captor.getValue();
        assertThat(withdrawn.status()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(withdrawn.withdrawnAt()).isEqualTo(NOW);
        assertThat(withdrawn.cancellationReason()).isEqualTo(CourseCancellationEnrollmentHandler.REASON);
        assertThat(withdrawn.approvedAt()).isEqualTo(approved.approvedAt());

        ArgumentCaptor<Enrollment> pending = ArgumentCaptor.forClass(Enrollment.class);
        verify(repository).updateState(pending.capture(), eq(EnrollmentStatus.APPLIED));
        assertThat(pending.getValue().status()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(pending.getValue().cancelledAt()).isEqualTo(NOW);
        assertThat(pending.getValue().cancellationReason()).isEqualTo(CourseCancellationEnrollmentHandler.REASON);
        verify(repository).updateState(any(), eq(EnrollmentStatus.WAITLISTED));
    }

    @Test
    void 이수_판정이_있는_수강은_건드리지_않는다() {
        Enrollment approved = enrollment(EnrollmentStatus.APPROVED);
        when(repository.findActiveByCourseId(COURSE_ID)).thenReturn(List.of(approved));
        when(repository.hasCompletionDecision(approved.id())).thenReturn(true);

        new CourseCancellationEnrollmentHandler(repository).onCourseCancelled(COURSE_ID, INSTITUTION_ID, NOW);

        verify(repository, never()).updateState(any(), any());
    }

    @Test
    void 동시에_바뀐_수강은_건너뛰고_나머지는_계속한다() {
        Enrollment first = enrollment(EnrollmentStatus.APPLIED);
        Enrollment second = enrollment(EnrollmentStatus.APPLIED);
        when(repository.findActiveByCourseId(COURSE_ID)).thenReturn(List.of(first, second));
        when(repository.hasCompletionDecision(any())).thenReturn(false);
        when(repository.updateState(any(), any())).thenReturn(false, true);

        new CourseCancellationEnrollmentHandler(repository).onCourseCancelled(COURSE_ID, INSTITUTION_ID, NOW);

        verify(repository, org.mockito.Mockito.times(2)).updateState(any(), eq(EnrollmentStatus.APPLIED));
    }

    private static Enrollment enrollment(EnrollmentStatus status) {
        Instant approvedAt = status == EnrollmentStatus.APPROVED ? APPLIED.plusSeconds(60) : null;
        return new Enrollment(UUID.randomUUID(), COURSE_ID, UUID.randomUUID(), null,
            EnrollmentApplicationType.SELF, status, APPLIED, approvedAt, null, null, null, null, null,
            APPLIED, APPLIED);
    }
}
