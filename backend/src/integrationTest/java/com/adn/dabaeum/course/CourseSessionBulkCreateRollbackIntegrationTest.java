package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.application.CourseSessionApplicationService;
import com.adn.dabaeum.course.application.CreateCourseSessionBulkCommand;
import com.adn.dabaeum.course.application.CreateCourseSessionCommand;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치의 항목 하나가 실패했을 때 이미 저장된 앞선 회차까지 실제로 롤백되는지 확인한다.
 * 목 리포지토리로는 트랜잭션 경계를 검증할 수 없어 실 DB 를 쓴다.
 *
 * <p>이 테스트는 {@link RemotePostgresIntegrationTestSupport} 의 클래스 레벨 {@code @Transactional}
 * 이 감싸는 테스트 트랜잭션 안에서 실행하면 안 된다 — 서비스의 {@code @Transactional} 이
 * REQUIRED 로 그 트랜잭션에 참여만 하게 되어, 실패 후에도 커밋 전 상태를 그대로 읽어 롤백을
 * 증명하지 못한다(같은 트랜잭션은 자신이 쓴 값을 항상 본다). 그래서 메서드에
 * {@code @Transactional(propagation = NOT_SUPPORTED)} 를 붙여 테스트 트랜잭션을 잠시 걷어내고,
 * 서비스가 자기 트랜잭션을 실제로 커밋/롤백하게 한 뒤 남은 행을 직접 정리한다.
 */
class CourseSessionBulkCreateRollbackIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CourseSessionApplicationService service;

    @Autowired
    CourseSessionRepository sessionRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    private UUID courseId;
    private UUID institutionId;

    @AfterEach
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanUp() {
        if (courseId != null) {
            jdbcTemplate.update("DELETE FROM tb_course_sessions WHERE course_id = ?", courseId);
            jdbcTemplate.update("DELETE FROM tb_courses WHERE id = ?", courseId);
        }
        // 이 테스트는 테스트 트랜잭션 밖에서 실커밋하므로 기관 행도 직접 지운다.
        // 안 지우면 실행할 때마다 공유 dev DB 에 고아 기관이 쌓여 기관 목록에 나온다.
        if (institutionId != null) {
            jdbcTemplate.update("DELETE FROM tb_institutions WHERE id = ?", institutionId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void COMPLETED_회차가_있으면_replaceExisting은_409이고_회차가_그대로_남는다() {
        courseId = setUpCourseWithExistingSession(1);
        CourseSession existing = sessionRepository.findByCourseId(courseId).get(0);
        // update() 의 가드는 COMPLETED/CANCELLED 로부터의 전이만 막을 뿐 그쪽으로의
        // 전이는 막지 않으므로, SCHEDULED -> COMPLETED 갱신은 그대로 통과한다.
        assertThat(sessionRepository.update(new CourseSession(
            existing.id(), existing.courseId(), existing.sessionNo(), existing.startsAt(),
            existing.endsAt(), existing.location(), existing.attendanceOpensAt(),
            existing.attendanceClosesAt(), CourseSessionStatus.COMPLETED,
            existing.createdAt(), existing.updatedAt(), existing.topic()))).isTrue();
        long before = sessionRepository.countByCourseId(courseId);

        ApiException conflict = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> service.createBulk(
                courseId,
                new CreateCourseSessionBulkCommand(List.of(newSessionCommand(1)), true),
                platformAdmin()),
            ApiException.class);

        assertThat(conflict.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT);
        assertThat(sessionRepository.countByCourseId(courseId)).isEqualTo(before);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 배치_중간_회차가_충돌하면_앞서_만든_회차까지_모두_롤백된다() {
        courseId = setUpCourseWithExistingSession(3);
        long before = sessionRepository.countByCourseId(courseId);

        assertThatThrownBy(() -> service.createBulk(
            courseId,
            new CreateCourseSessionBulkCommand(List.of(
                newSessionCommand(1),
                newSessionCommand(2),
                newSessionCommand(3)
            ), false),
            platformAdmin()
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.code()).isEqualTo(ApiErrorCode.COURSE_SESSION_CONFLICT));

        long after = sessionRepository.countByCourseId(courseId);
        assertThat(after).isEqualTo(before);
    }

    private UUID setUpCourseWithExistingSession(int existingSessionNo) {
        institutionId = UUID.randomUUID();
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        institutionRepository.save(new Institution(
            institutionId, uniqueCode("rb-inst"), "롤백 테스트 기관", null, null, null, null,
            null, InstitutionStatus.ACTIVE, now, now, null));
        UUID id = UUID.randomUUID();
        Course course = new Course(id, institutionId, uniqueCode("rb-course"), "롤백 테스트 과정",
            null, null, CourseEducationType.HYBRID, LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 1), null, null, 10, null, null, false, null,
            CourseStatus.DRAFT, now, now, null);
        courseRepository.save(course);

        Instant starts = Instant.parse("2099-09-01T00:00:00Z")
            .plusSeconds(existingSessionNo * 86400L);
        sessionRepository.save(new CourseSession(
            UUID.randomUUID(), id, existingSessionNo, starts, starts.plusSeconds(7200), null,
            null, null, CourseSessionStatus.SCHEDULED, now, now, null));
        return id;
    }

    private CreateCourseSessionCommand newSessionCommand(int sessionNo) {
        Instant starts = Instant.parse("2099-09-01T00:00:00Z").plusSeconds(sessionNo * 86400L);
        return new CreateCourseSessionCommand(
            courseId, sessionNo, starts, starts.plusSeconds(7200), null, null, null, null,
            CourseSessionStatus.SCHEDULED);
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
