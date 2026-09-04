package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import java.util.UUID;

public interface EnrollmentApplicationService {

    Enrollment createSelf(
        CreateEnrollmentCommand command,
        AuthenticatedUserContext context
    );

    Enrollment createProxy(
        CreateProxyEnrollmentCommand command,
        AuthenticatedUserContext context
    );

    EnrollmentPage list(
        ListCourseEnrollmentsQuery query,
        AuthenticatedUserContext context
    );

    Enrollment get(UUID enrollmentId, AuthenticatedUserContext context);

    Enrollment approve(UUID enrollmentId, AuthenticatedUserContext context);

    Enrollment reject(
        RejectEnrollmentCommand command,
        AuthenticatedUserContext context
    );

    Enrollment cancel(UUID enrollmentId, AuthenticatedUserContext context);

    Enrollment withdraw(UUID enrollmentId, AuthenticatedUserContext context);
}
