package com.adn.dabaeum.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.authentication.application.LocalJwtTokenService;
import com.adn.dabaeum.authentication.application.LoginCommand;
import com.adn.dabaeum.authentication.application.SignupCommand;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand;
import com.adn.dabaeum.enrollment.application.EnrollmentApplicationService;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalAccountIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired LocalAccountApplicationService localAccountService;
    @Autowired LocalJwtTokenService localJwtTokenService;
    @Autowired UserRepository userRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentApplicationService enrollmentService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void signsUpLogsInAndUsesGlobalLearnerInsideRollback() {
        String email = "integration-" + UUID.randomUUID() + "@example.com";
        String password = "learning-2026!";
        SignupCommand signupCommand = new SignupCommand(
            email,
            password,
            "통합 학습자",
            "010-0000-0000",
            LocalDate.of(1995, 5, 10)
        );

        LocalAuthResult signup = localAccountService.signup(signupCommand);
        UUID userId = signup.userId();
        assertThat(signup.provider()).isEqualTo("LOCAL");
        assertThat(signup.roles()).containsExactly(
            new AuthenticatedRole("LEARNER", null)
        );
        assertThat(userRepository.findById(userId)).get()
            .extracting(user -> user.email())
            .isEqualTo(email);

        Authentication authentication = localJwtTokenService.authenticate(
            signup.accessToken()
        );
        assertThat(authentication.getPrincipal())
            .isEqualTo(new AuthenticatedUserPrincipal(
                userId,
                "LOCAL",
                java.util.Set.of("LEARNER")
            ));
        assertThat(authentication.getDetails())
            .isInstanceOf(AuthenticatedTokenDetails.class);
        AuthenticatedTokenDetails tokenDetails =
            (AuthenticatedTokenDetails) authentication.getDetails();
        assertThat(tokenDetails.context().roles()).containsExactly(
            new AuthenticatedRole("LEARNER", null)
        );

        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(
            UUID.randomUUID(),
            uniqueCode("LOCAL-INST"),
            "LOCAL 회원가입 통합 기관",
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            now,
            now,
            null
        );
        institutionRepository.save(institution);
        Course course = new Course(
            UUID.randomUUID(),
            institution.id(),
            uniqueCode("LOCAL-COURSE"),
            "전역 학습자 통합 과정",
            null,
            null,
            CourseEducationType.ONLINE,
            LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 1),
            null,
            null,
            10,
            null,
            "https://example.test/local-course",
            false,
            null,
            CourseStatus.RECRUITING,
            now,
            now,
            null
        );
        courseRepository.save(course);
        Enrollment enrollment = enrollmentService.createSelf(
            new CreateEnrollmentCommand(
                course.id(),
                userId,
                EnrollmentApplicationType.SELF
            ),
            tokenDetails.context()
        );
        assertThat(enrollment.courseId()).isEqualTo(course.id());
        assertThat(enrollment.userId()).isEqualTo(userId);
        assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.APPLIED);

        LocalAuthResult login = localAccountService.login(
            new LoginCommand(email, password)
        );
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.userId()).isEqualTo(userId);

        String passwordHash = jdbcTemplate.queryForObject("""
            SELECT password_hash
              FROM tb_user_identities
             WHERE provider = 'LOCAL'
               AND provider_subject = ?
            """, String.class, email);
        assertThat(passwordHash).isNotEqualTo(password);
        assertThat(passwordHash).matches("^\\$2[aby]\\$12\\$.{53}$");
        assertThat(passwordEncoder.matches(password, passwordHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM tb_user_roles
             WHERE user_id = ?
               AND institution_id IS NULL
               AND role = 'LEARNER'
            """, Integer.class, userId)).isEqualTo(1);

        assertThatThrownBy(() -> localAccountService.signup(signupCommand))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.IDENTITY_CONFLICT);
            });
    }
}
