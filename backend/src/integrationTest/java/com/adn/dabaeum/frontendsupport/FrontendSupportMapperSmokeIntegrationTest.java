package com.adn.dabaeum.frontendsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.PasswordResetTokenRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository;
import com.adn.dabaeum.blockchain.domain.BlockchainMonitoringRepository;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationRepository;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import com.adn.dabaeum.interest.domain.CourseInterestRepository;
import com.adn.dabaeum.notification.domain.NotificationRepository;
import com.adn.dabaeum.review.domain.CourseReviewRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.support.domain.CommonCode;
import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.CommonCodeRepository;
import com.adn.dabaeum.support.domain.FaqRepository;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeRepository;
import com.adn.dabaeum.support.domain.TermsContentRepository;
import com.adn.dabaeum.support.domain.TermsType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 프론트 미구현 API 구현으로 추가된 MyBatis 매퍼 SQL이 실제 PostgreSQL에서
 * 실행 가능한지 확인하는 스모크 테스트다.
 *
 * <p>슬라이스 테스트는 리포지토리를 mock으로 대체하므로 SQL 문자열을 검증하지 못한다.
 * 이 테스트는 각 쿼리를 실제로 실행해 컬럼명·JOIN·문법 오류를 잡는다.
 * 데이터를 넣지 않으므로 결과는 비어 있으며, 검증 대상은 "오류 없이 실행되는가"다.
 * 상위 클래스가 트랜잭션 롤백을 보장한다.
 */
class FrontendSupportMapperSmokeIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    private static final UUID ABSENT = UUID.fromString(
        "00000000-0000-0000-0000-000000000000");

    @Autowired EnrollmentQueryRepository enrollmentQueryRepository;
    @Autowired CourseQueryRepository courseQueryRepository;
    @Autowired LearningBadgeRepository badgeRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired FaqRepository faqRepository;
    @Autowired TermsContentRepository termsRepository;
    @Autowired CommonCodeRepository codeRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired CourseReviewRepository reviewRepository;
    @Autowired CourseInterestRepository interestRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired InstitutionJoinApplicationRepository joinApplicationRepository;
    @Autowired DashboardRepository dashboardRepository;
    @Autowired BlockchainMonitoringRepository blockchainMonitoringRepository;
    @Autowired PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired LocalAccountRepository localAccountRepository;
    @Autowired StoredFileRepository storedFileRepository;

    @Test
    void enrollmentQueriesExecuteAgainstRemoteSchema() {
        assertThatCode(() -> {
            enrollmentQueryRepository.findMyEnrollments(ABSENT, null, 20, 0, "appliedAt,desc");
            enrollmentQueryRepository.findMyEnrollments(
                ABSENT, EnrollmentStatus.APPROVED, 20, 0, "createdAt,asc");
            enrollmentQueryRepository.countMyEnrollments(ABSENT, null);
            enrollmentQueryRepository.countMyEnrollments(ABSENT, EnrollmentStatus.APPLIED);
            enrollmentQueryRepository.findInstitutionEnrollments(
                ABSENT, null, 20, 0, "appliedAt,desc");
            enrollmentQueryRepository.countInstitutionEnrollments(
                ABSENT, EnrollmentStatus.APPROVED);
        }).doesNotThrowAnyException();
    }

    @Test
    void learningSummaryAndProgressQueriesExecuteAgainstRemoteSchema() {
        assertThat(enrollmentQueryRepository.learningSummary(ABSENT)).isNotNull();

        assertThatCode(() -> {
            enrollmentQueryRepository.findLearningCourses(ABSENT, null, 20, 0);
            enrollmentQueryRepository.findLearningCourses(ABSENT, "IN_PROGRESS", 20, 0);
            enrollmentQueryRepository.countLearningCourses(ABSENT, "COMPLETED");
            enrollmentQueryRepository.findInstructorEnrollmentProgress(ABSENT, null, 20, 0);
            enrollmentQueryRepository.findInstructorEnrollmentProgress(ABSENT, ABSENT, 20, 0);
            enrollmentQueryRepository.countInstructorEnrollmentProgress(ABSENT, null);
        }).doesNotThrowAnyException();

        assertThat(enrollmentQueryRepository.findEnrollmentProgress(ABSENT)).isEmpty();
    }

    @Test
    void courseQueriesExecuteAgainstRemoteSchema() {
        assertThatCode(() -> {
            courseQueryRepository.findInstructorCourses(ABSENT, null, 20, 0, "createdAt,desc");
            courseQueryRepository.findInstructorCourses(
                ABSENT, CourseStatus.IN_PROGRESS, 20, 0, "title,asc");
            courseQueryRepository.countInstructorCourses(ABSENT, null);
            courseQueryRepository.findInstitutionCourses(
                ABSENT, null, 20, 0, "startDate,desc");
            courseQueryRepository.countInstitutionCourses(ABSENT, CourseStatus.RECRUITING);
            courseQueryRepository.findInstitutionInstructors(
                ABSENT, null, 20, 0, "joinedAt,desc");
            courseQueryRepository.findInstitutionInstructors(
                ABSENT, "ACTIVE", 20, 0, "name,asc");
            courseQueryRepository.countInstitutionInstructors(ABSENT, "ACTIVE");
        }).doesNotThrowAnyException();

        assertThat(courseQueryRepository.instructorCourseStats(ABSENT)).isNotNull();
    }

    @Test
    void storedFileQueriesExecuteAgainstRemoteSchema() {
        assertThat(storedFileRepository.findById(ABSENT)).isEmpty();
    }

    @Test
    void badgeQueriesExecuteAgainstRemoteSchema() {
        assertThat(badgeRepository.findById(ABSENT)).isEmpty();
        assertThat(badgeRepository.findByCredentialIdAndTypeAndName(
            ABSENT, "COURSE_COMPLETION", "smoke")).isEmpty();
        assertThat(badgeRepository.findByUserId(ABSENT, 20, 0, "createdAt,desc")).isEmpty();
        assertThat(badgeRepository.countByUserId(ABSENT)).isZero();
    }

    @Test
    void supportQueriesExecuteAgainstRemoteSchema() {
        // 공유 개발 DB 에는 실제 공지·FAQ·약관·코드가 있을 수 있다. 비어 있다고 가정하지 않고
        // 이 테스트가 넣은 행만 찾아 확인한다. 넣은 행은 트랜잭션과 함께 롤백된다.
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime farFuture = OffsetDateTime.parse("2099-01-01T00:00:00Z");

        UUID noticeId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_notices (id, title, body, audience, status, published_at, created_at, updated_at)
            VALUES (?, ?, ?, 'INSTRUCTOR', 'PUBLISHED', ?, ?, ?)
            """, noticeId, uniqueCode("notice"), "smoke", farFuture, farFuture, farFuture);
        assertThat(noticeRepository.findById(ABSENT)).isEmpty();
        assertThat(noticeRepository.findById(noticeId)).map(Notice::id).contains(noticeId);
        assertThat(noticeRepository.findPublishedPage(null, 20, 0))
            .extracting(Notice::id).contains(noticeId);
        assertThat(noticeRepository.findPublishedPage(NoticeAudience.INSTRUCTOR, 20, 0))
            .extracting(Notice::id).contains(noticeId);
        assertThat(noticeRepository.countPublished(NoticeAudience.INSTRUCTOR)).isPositive();
        assertThat(noticeRepository.findAdminPage(20, 0)).extracting(Notice::id).contains(noticeId);
        assertThat(noticeRepository.countAll()).isPositive();

        UUID faqId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_faqs (id, question, answer, sort_order, created_at, updated_at)
            VALUES (?, ?, ?, -1, ?, ?)
            """, faqId, uniqueCode("faq"), "smoke", now, now);
        assertThat(faqRepository.findPage(20, 0)).extracting(Faq::id).contains(faqId);
        assertThat(faqRepository.countAll()).isPositive();

        UUID termsId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_terms_contents (id, type, title, body, version, created_at, updated_at)
            VALUES (?, 'USAGE', ?, ?, ?, ?, ?)
            """, termsId, "smoke", "smoke",
            "smoke-" + termsId.toString().substring(0, 8), farFuture, farFuture);   // version 은 30자 제한
        assertThat(termsRepository.findLatestByType(TermsType.USAGE))
            .map(TermsContent::id).contains(termsId);

        String codeGroup = uniqueCode("GROUP");
        UUID codeId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_common_codes (id, code_group, code, name, sort_order, created_at, updated_at)
            VALUES (?, ?, 'SMOKE', 'smoke', 0, ?, ?)
            """, codeId, codeGroup, now, now);
        assertThat(codeRepository.findByGroup(codeGroup)).extracting(CommonCode::id).containsExactly(codeId);
    }

    @Test
    void inquiryQueriesExecuteAgainstRemoteSchema() {
        assertThat(inquiryRepository.findById(ABSENT)).isEmpty();

        assertThatCode(() -> {
            inquiryRepository.findByUserId(ABSENT, null, 20, 0);
            inquiryRepository.findByUserId(ABSENT, InquiryStatus.PENDING, 20, 0);
            inquiryRepository.countByUserId(ABSENT, InquiryStatus.ANSWERED);
            inquiryRepository.findByInstructorUserId(ABSENT, null, null, 20, 0);
            inquiryRepository.findByInstructorUserId(
                ABSENT, InquiryStatus.PENDING, ABSENT, 20, 0);
            inquiryRepository.countByInstructorUserId(ABSENT, null, null);
            inquiryRepository.findByInstitutionId(ABSENT, null, null, 20, 0);
            inquiryRepository.countByInstitutionId(ABSENT, InquiryStatus.PENDING, ABSENT);
        }).doesNotThrowAnyException();
    }

    @Test
    void reviewInterestAndNotificationQueriesExecuteAgainstRemoteSchema() {
        assertThat(reviewRepository.existsByUserIdAndCourseId(ABSENT, ABSENT)).isFalse();
        assertThat(reviewRepository.hasConfirmedCompletion(ABSENT, ABSENT)).isFalse();
        assertThat(reviewRepository.findByUserId(ABSENT, 20, 0)).isEmpty();
        assertThat(reviewRepository.countByCourseId(ABSENT)).isZero();
        assertThat(reviewRepository.findByCourseId(ABSENT, 20, 0)).isEmpty();

        assertThat(interestRepository.findById(ABSENT)).isEmpty();
        assertThat(interestRepository.findByUserIdAndCourseId(ABSENT, ABSENT)).isEmpty();
        assertThat(interestRepository.findByUserId(ABSENT, 20, 0)).isEmpty();
        assertThat(interestRepository.countByUserId(ABSENT)).isZero();

        assertThat(notificationRepository.findById(ABSENT)).isEmpty();
        assertThat(notificationRepository.findByUserId(ABSENT, false, 20, 0)).isEmpty();
        assertThat(notificationRepository.findByUserId(ABSENT, true, 20, 0)).isEmpty();
        assertThat(notificationRepository.countByUserId(ABSENT, true)).isZero();
        assertThat(notificationRepository.countUnread(ABSENT)).isZero();
    }

    @Test
    void institutionApplicationAndDashboardQueriesExecuteAgainstRemoteSchema() {
        assertThat(joinApplicationRepository.findById(ABSENT)).isEmpty();
        assertThat(joinApplicationRepository.findPage(null, 20, 0)).isEmpty();
        assertThat(joinApplicationRepository.findPage(
            InstitutionJoinApplicationStatus.PENDING, 20, 0)).isEmpty();
        assertThat(joinApplicationRepository.count(
            InstitutionJoinApplicationStatus.APPROVED)).isZero();

        assertThat(dashboardRepository.platformDashboard(5)).isNotNull();
        assertThat(dashboardRepository.institutionDashboard(ABSENT)).isNotNull();
    }

    @Test
    void blockchainMonitoringAndAccountQueriesExecuteAgainstRemoteSchema() {
        assertThat(blockchainMonitoringRepository.metrics()).isNotNull();
        assertThatCode(() -> {
            blockchainMonitoringRepository.findTransactions(20, 0);
            blockchainMonitoringRepository.countTransactions();
            blockchainMonitoringRepository.findAlerts(20, 0);
            blockchainMonitoringRepository.countAlerts();
        }).doesNotThrowAnyException();

        assertThat(passwordResetTokenRepository.findByTokenHash("0".repeat(64))).isEmpty();
        assertThat(localAccountRepository.findByUserId(ABSENT)).isEmpty();
    }
}
