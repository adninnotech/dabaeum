package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 수강신청을 승인·반려할 수 있는 사람인지 검사한다.
 *
 * <p>건별 처리와 일괄 처리가 같은 규칙을 써야 해서 서비스의 private 메서드에서 빼냈다.
 * 규칙이 갈리면 일괄 처리만 남의 기관 건을 통과시키는 구멍이 된다.
 */
@Component
public class EnrollmentDecisionAuthority {

    private final CourseInstructorRepository instructorRepository;
    private final AuthorizationPolicy authorizationPolicy;

    public EnrollmentDecisionAuthority(
        CourseInstructorRepository instructorRepository,
        AuthorizationPolicy authorizationPolicy
    ) {
        this.instructorRepository =
            Objects.requireNonNull(instructorRepository, "instructorRepository");
        this.authorizationPolicy =
            Objects.requireNonNull(authorizationPolicy, "authorizationPolicy");
    }

    public void require(AuthenticatedUserContext context, Course course) {
        authorizationPolicy.requireEnrollmentDecisionManager(
            context, course.institutionId(), mainInstructor(context, course));
    }

    public boolean allows(AuthenticatedUserContext context, Course course) {
        return authorizationPolicy.isEnrollmentDecisionManager(
            context, course.institutionId(), mainInstructor(context, course));
    }

    private boolean mainInstructor(AuthenticatedUserContext context, Course course) {
        return context != null
            && instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
    }
}
