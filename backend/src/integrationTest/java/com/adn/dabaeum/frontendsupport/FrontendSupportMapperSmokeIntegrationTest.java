package com.adn.dabaeum.frontendsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.PasswordResetTokenRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository;
import com.adn.dabaeum.blockchain.domain.BlockchainMonitoringRepository;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
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
import java.time.Instant;
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
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseQueryRepository courseQueryRepository;
    @Autowired LearningBadgeRepository badgeRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired com.adn.dabaeum.support.domain.NoticeAttachmentRepository noticeAttachmentRepository;
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
            // 사이트 전체 수강신청 목록(수강현황관리). 필터를 모두 채워 SQL 문법 오류를 잡는다.
            enrollmentQueryRepository.findAllEnrollments(
                null, null, null, null, null, 20, 0, "appliedAt,desc");
            enrollmentQueryRepository.findAllEnrollments(
                EnrollmentStatus.APPROVED, ABSENT, ABSENT, CourseEducationType.OFFLINE,
                "smoke", 20, 0, "createdAt,asc");
            enrollmentQueryRepository.countAllEnrollments(null, null, null, null, null);
            enrollmentQueryRepository.countAllEnrollments(
                EnrollmentStatus.APPROVED, ABSENT, ABSENT, CourseEducationType.OFFLINE, "smoke");
        }).doesNotThrowAnyException();
        // 일괄 승인·반려의 IN 조회. foreach 의 UUID 바인딩이 실제 Postgres 에서 도는지 본다.
        assertThat(enrollmentRepository.findAllByIds(java.util.List.of(ABSENT))).isEmpty();
    }

    @Test
    void learningSummaryAndProgressQueriesExecuteAgainstRemoteSchema() {
        assertThat(enrollmentQueryRepository.learningSummary(ABSENT)).isNotNull();

        assertThatCode(() -> {
            enrollmentQueryRepository.findLearningCourses(ABSENT, null, null, 20, 0);
            enrollmentQueryRepository.findLearningCourses(ABSENT, "IN_PROGRESS", null, 20, 0);
            enrollmentQueryRepository.countLearningCourses(ABSENT, "COMPLETED", null);
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
                ABSENT, null, null, 20, 0, "joinedAt,desc");
            courseQueryRepository.findInstitutionInstructors(
                ABSENT, "ACTIVE", "smoke", 20, 0, "name,asc");
            courseQueryRepository.countInstitutionInstructors(ABSENT, "ACTIVE", "smoke");
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

        // 이 공지는 게시일이 2099년이라 예약 게시분이다. 공개 목록에서는 지금 빠져야 하고,
        // 그 시각을 지나면 나와야 한다. 관리자 목록에는 시각과 무관하게 나온다.
        java.time.Instant nowInstant = java.time.Instant.now();
        java.time.Instant afterFarFuture = farFuture.toInstant().plusSeconds(1);
        assertThat(noticeRepository.findPublishedPage(null, 20, 0, nowInstant))
            .extracting(Notice::id).doesNotContain(noticeId);
        assertThat(noticeRepository.findPublishedPage(
                NoticeAudience.INSTRUCTOR, 20, 0, afterFarFuture))
            .extracting(Notice::id).contains(noticeId);
        assertThat(noticeRepository.countPublished(NoticeAudience.INSTRUCTOR, afterFarFuture))
            .isGreaterThan(noticeRepository.countPublished(
                NoticeAudience.INSTRUCTOR, nowInstant));
        assertThat(noticeRepository.findAdminPage(20, 0, null, null, null))
            .extracting(Notice::id).contains(noticeId);
        assertThat(noticeRepository.countAll(null, null, null)).isPositive();

        // q(제목)·audience·status 필터. 고유 제목이라 이 공지 하나만 걸린다.
        String titleKeyword = com.adn.dabaeum.common.api.KeywordQuery.normalize(
            jdbcTemplate.queryForObject(
                "SELECT title FROM tb_notices WHERE id = ?", String.class, noticeId));
        assertThat(noticeRepository.findAdminPage(
                20, 0, titleKeyword, NoticeAudience.INSTRUCTOR,
                com.adn.dabaeum.support.domain.NoticeStatus.PUBLISHED))
            .extracting(Notice::id).containsExactly(noticeId);
        assertThat(noticeRepository.countAll(
                titleKeyword, NoticeAudience.INSTRUCTOR,
                com.adn.dabaeum.support.domain.NoticeStatus.PUBLISHED))
            .isEqualTo(1);
        assertThat(noticeRepository.countAll(
                titleKeyword, NoticeAudience.PUBLIC,
                com.adn.dabaeum.support.domain.NoticeStatus.PUBLISHED))
            .isZero();

        // 첨부 왕복: 붙이고 목록에 보이고 떼면 사라진다. purpose CHECK 에 NOTICE_ATTACHMENT 가
        // 들어갔는지, 부분 유니크 인덱스가 소프트 삭제 뒤 재첨부를 막지 않는지 같이 본다.
        UUID uploaderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            uploaderId, uniqueCode("uploader"), uniqueCode("uploader") + "@example.test");
        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_files
                (id, purpose, original_name, content_type, size, storage_path,
                 uploaded_by, created_at)
            VALUES (?, 'NOTICE_ATTACHMENT', ?, 'application/pdf', 1024, ?, ?, ?)
            """, fileId, "안내문.pdf", "notices/" + fileId, uploaderId, now);
        UUID attachmentId = UUID.randomUUID();
        noticeAttachmentRepository.save(new com.adn.dabaeum.support.domain.NoticeAttachment(
            attachmentId, noticeId, fileId, "안내문.pdf", 1024L, 0,
            java.time.Instant.now()));
        assertThat(noticeAttachmentRepository.findActiveByNoticeId(noticeId))
            .extracting(com.adn.dabaeum.support.domain.NoticeAttachment::id)
            .containsExactly(attachmentId);
        assertThat(noticeAttachmentRepository.findActiveById(attachmentId)).isPresent();
        assertThat(noticeAttachmentRepository.softDelete(attachmentId, java.time.Instant.now()))
            .isTrue();
        assertThat(noticeAttachmentRepository.findActiveByNoticeId(noticeId)).isEmpty();

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
            inquiryRepository.findByUserId(ABSENT, null, null, 20, 0);
            inquiryRepository.findByUserId(ABSENT, InquiryStatus.PENDING, null, 20, 0);
            inquiryRepository.countByUserId(ABSENT, InquiryStatus.ANSWERED, null);
            inquiryRepository.findByInstructorUserId(ABSENT, null, null, 20, 0);
            inquiryRepository.findByInstructorUserId(
                ABSENT, InquiryStatus.PENDING, ABSENT, 20, 0);
            inquiryRepository.countByInstructorUserId(ABSENT, null, null);
            inquiryRepository.findByInstitutionId(ABSENT, null, null, 20, 0);
            inquiryRepository.countByInstitutionId(ABSENT, InquiryStatus.PENDING, ABSENT);
            // B-7 사이트 전체 문의 목록. 필터를 모두 채워 SQL 문법 오류를 잡는다.
            inquiryRepository.findForAdmin(null, null, null, null, null, null, 20, 0);
            inquiryRepository.findForAdmin(
                InquiryStatus.PENDING, InquiryType.COURSE, CourseEducationType.OFFLINE,
                ABSENT, ABSENT, "smoke", 20, 0);
            inquiryRepository.countForAdmin(null, null, null, null, null, null);
            inquiryRepository.countForAdmin(
                InquiryStatus.PENDING, InquiryType.COURSE, CourseEducationType.OFFLINE,
                ABSENT, ABSENT, "smoke");
        }).doesNotThrowAnyException();
    }

    @Test
    void reviewInterestAndNotificationQueriesExecuteAgainstRemoteSchema() {
        assertThat(reviewRepository.existsByUserIdAndCourseId(ABSENT, ABSENT)).isFalse();
        assertThat(reviewRepository.hasConfirmedCompletion(ABSENT, ABSENT)).isFalse();
        assertThat(reviewRepository.findByUserId(ABSENT, null, 20, 0)).isEmpty();
        assertThat(reviewRepository.countByCourseId(ABSENT)).isZero();
        assertThat(reviewRepository.findByCourseId(ABSENT, 20, 0)).isEmpty();

        assertThat(interestRepository.findById(ABSENT)).isEmpty();
        assertThat(interestRepository.findByUserIdAndCourseId(ABSENT, ABSENT)).isEmpty();
        assertThat(interestRepository.findByUserId(ABSENT, null, 20, 0)).isEmpty();
        assertThat(interestRepository.countByUserId(ABSENT, null)).isZero();

        assertThat(notificationRepository.findById(ABSENT)).isEmpty();
        assertThat(notificationRepository.findByUserId(ABSENT, false, 20, 0)).isEmpty();
        assertThat(notificationRepository.findByUserId(ABSENT, true, 20, 0)).isEmpty();
        assertThat(notificationRepository.countByUserId(ABSENT, true)).isZero();
        assertThat(notificationRepository.countUnread(ABSENT)).isZero();
    }

    @Test
    void institutionApplicationAndDashboardQueriesExecuteAgainstRemoteSchema() {
        assertThat(joinApplicationRepository.findById(ABSENT)).isEmpty();
        // 공유 dev DB 라 테이블이 비었다고 가정할 수 없다. 쿼리가 실제 스키마에서 도는지만 본다.
        assertThatCode(() -> {
            joinApplicationRepository.findPage(null, null, 20, 0);
            joinApplicationRepository.findPage(
                InstitutionJoinApplicationStatus.PENDING, ABSENT, 20, 0);
            joinApplicationRepository.count(
                InstitutionJoinApplicationStatus.APPROVED, ABSENT);
            joinApplicationRepository.existsPendingByApplicant(ABSENT);
        }).doesNotThrowAnyException();

        assertThat(dashboardRepository.platformDashboard(5, Instant.now())).isNotNull();
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
