package com.adn.dabaeum.completion.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.Completion;
import java.util.UUID;

public interface CompletionApplicationService {

    Completion get(UUID enrollmentId, AuthenticatedUserContext context);

    Completion evaluate(EvaluateCompletionCommand command, AuthenticatedUserContext context);

    Completion confirm(UUID enrollmentId, AuthenticatedUserContext context);

    /** 관리자 정정: 확정된 이수를 사유와 함께 되돌린다. 수료증이 발급됐으면 거부한다. */
    Completion revertConfirmation(
        UUID enrollmentId, String reason, AuthenticatedUserContext context);
}
