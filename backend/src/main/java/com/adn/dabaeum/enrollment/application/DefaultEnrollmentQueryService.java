package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.attendance.application.AttendanceMetricsQuery;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.enrollment.domain.EnrollmentProgressView;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.UserLearningStats;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultEnrollmentQueryService implements EnrollmentQueryService {

    private final EnrollmentQueryRepository queryRepository;
    private final AuthorizationPolicy authorizationPolicy;
    private final UserRepository userRepository;
    private final AttendanceMetricsQuery metricsQuery;
    private final Clock clock;

    public DefaultEnrollmentQueryService(
        EnrollmentQueryRepository queryRepository,
        AuthorizationPolicy authorizationPolicy,
        UserRepository userRepository,
        AttendanceMetricsQuery metricsQuery,
        Clock clock
    ) {
        this.queryRepository = queryRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.userRepository = userRepository;
        this.metricsQuery = Objects.requireNonNull(metricsQuery);
        this.clock = Objects.requireNonNull(clock);
    }

    /*
     * me 메서드는 본문을 갖지 않고 userId 판 메서드에 그대로 넘긴다. 자기 호출이라 트랜잭션
     * 프록시를 타지 않지만 두 메서드의 트랜잭션 속성이 (readOnly = true) 로 같아서 문제가 없다.
     * 속성이 갈라지면 본문을 private 메서드로 빼야 한다.
     */

    @Override
    @Transactional(readOnly = true)
    public MyEnrollmentPage listMyEnrollments(
        AuthenticatedUserContext actor, EnrollmentStatus status,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        return listEnrollmentsOf(actor.userId(), status, page, size, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public MyEnrollmentPage listEnrollmentsOf(
        UUID userId, EnrollmentStatus status, int page, int size, String sort
    ) {
        requireUser(userId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findMyEnrollments(
            userId, status, size, offset, sort);
        long totalElements = queryRepository.countMyEnrollments(userId, status);
        return new MyEnrollmentPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionEnrollmentPage listInstitutionEnrollments(
        AuthenticatedUserContext actor, UUID institutionId, EnrollmentStatus status,
        int page, int size, String sort
    ) {
        Objects.requireNonNull(actor, "actor");
        authorizationPolicy.requireCourseManager(actor, institutionId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findInstitutionEnrollments(
            institutionId, status, size, offset, sort);
        long totalElements = queryRepository.countInstitutionEnrollments(
            institutionId, status);
        return new InstitutionEnrollmentPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionEnrollmentPage listAllEnrollments(
        EnrollmentStatus status, UUID institutionId, UUID courseId,
        com.adn.dabaeum.course.domain.CourseEducationType educationType, String keyword,
        int page, int size, String sort
    ) {
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findAllEnrollments(
            status, institutionId, courseId, educationType, keyword, size, offset, sort);
        long totalElements = queryRepository.countAllEnrollments(
            status, institutionId, courseId, educationType, keyword);
        return new InstitutionEnrollmentPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public com.adn.dabaeum.enrollment.domain.LearningSummary learningSummary(
        AuthenticatedUserContext actor
    ) {
        Objects.requireNonNull(actor, "actor");
        return learningSummaryOf(actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public com.adn.dabaeum.enrollment.domain.LearningSummary learningSummaryOf(UUID userId) {
        requireUser(userId);
        return queryRepository.learningSummary(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserLearningStats learningStatsOf(UUID userId) {
        // 요약 규칙을 여기서 다시 쓰지 않는다 — learningSummaryOf 가 세는 값을 그대로 합친다.
        return UserLearningStats.of(
            learningSummaryOf(userId), queryRepository.learningActivity(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public LearningCoursePage listLearningCourses(
        AuthenticatedUserContext actor, String learningStatus, String keyword,
        int page, int size
    ) {
        Objects.requireNonNull(actor, "actor");
        return listLearningCoursesOf(actor.userId(), learningStatus, keyword, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public LearningCoursePage listLearningCoursesOf(
        UUID userId, String learningStatus, String keyword, int page, int size
    ) {
        requireUser(userId);
        int offset = Math.multiplyExact(page, size);
        var data = queryRepository.findLearningCourses(
            userId, learningStatus, keyword, size, offset);
        long totalElements = queryRepository.countLearningCourses(
            userId, learningStatus, keyword);
        return new LearningCoursePage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentProgressPage listInstructorEnrollmentProgress(
        AuthenticatedUserContext actor, UUID courseId, int page, int size
    ) {
        // 본문은 listInstructorEnrollmentProgressOf 한 곳에만 둔다. 자기 호출이라 트랜잭션
        // 프록시를 안 타지만 두 메서드의 속성이 (readOnly = true) 로 같아서 문제가 없다.
        Objects.requireNonNull(actor, "actor");
        return listInstructorEnrollmentProgressOf(actor.userId(), courseId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentProgressPage listInstructorEnrollmentProgressOf(
        UUID userId, UUID courseId, int page, int size
    ) {
        requireUser(userId);
        int offset = Math.multiplyExact(page, size);
        var data = withAttendanceRates(queryRepository.findInstructorEnrollmentProgress(
            userId, courseId, size, offset));
        long totalElements = queryRepository.countInstructorEnrollmentProgress(
            userId, courseId);
        return new EnrollmentProgressPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public com.adn.dabaeum.enrollment.domain.EnrollmentProgressView enrollmentProgress(
        AuthenticatedUserContext actor, UUID enrollmentId
    ) {
        Objects.requireNonNull(actor, "actor");
        var progress = queryRepository.findEnrollmentProgress(enrollmentId)
            .orElseThrow(() -> new com.adn.dabaeum.common.api.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                com.adn.dabaeum.common.api.ApiErrorCode.ENROLLMENT_NOT_FOUND,
                "Enrollment not found"));
        if (!Objects.equals(actor.userId(), progress.userId())) {
            authorizationPolicy.requirePlatformAdmin(actor);
        }
        return withAttendanceRate(progress);
    }

    /**
     * 출석률은 학습자 출결 요약(GET /enrollments/{id}/attendance-summary)과 같은 계산에서 가져온다.
     * SQL 로 따로 세면 두 화면 숫자가 또 갈라진다(QA DATA-04). 페이지 크기만큼 계산하므로 목록 크기 제한을 따른다.
     */
    private List<EnrollmentProgressView> withAttendanceRates(List<EnrollmentProgressView> views) {
        return views.stream().map(this::withAttendanceRate).toList();
    }

    private EnrollmentProgressView withAttendanceRate(EnrollmentProgressView view) {
        return view.withAttendanceRate(
            metricsQuery.calculate(view.enrollmentId(), clock.instant()).attendanceRate());
    }

    /** 없는 사용자에게는 빈 페이지가 아니라 404 를 준다 — 관리자 화면이 오탈자를 알아채야 한다. */
    private void requireUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        if (userRepository.findById(userId).isEmpty()) {
            throw new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "User not found");
        }
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
