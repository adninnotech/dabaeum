package com.adn.dabaeum.instructor.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.UUID;

public interface InstructorApplicationService {

    InstructorApplicationView apply(
        ApplyInstructorCommand command,
        AuthenticatedUserContext context
    );

    InstructorApplicationView get(UUID applicationId, AuthenticatedUserContext context);

    InstructorApplicationPage listMine(
        ListInstructorApplicationsQuery query,
        AuthenticatedUserContext context
    );

    InstructorApplicationPage listInstitution(
        ListInstructorApplicationsQuery query,
        AuthenticatedUserContext context
    );

    InstructorApplicationView approve(
        UUID applicationId,
        AuthenticatedUserContext context
    );

    InstructorApplicationView reject(
        RejectInstructorApplicationCommand command,
        AuthenticatedUserContext context
    );
}
