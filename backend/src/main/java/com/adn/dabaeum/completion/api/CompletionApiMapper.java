package com.adn.dabaeum.completion.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.completion.application.EvaluateCompletionCommand;
import com.adn.dabaeum.completion.domain.Completion;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CompletionApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public CompletionApiMapper(Clock clock) {
        this.clock = clock;
    }

    public EvaluateCompletionCommand toEvaluateCommand(
        UUID enrollmentId,
        CompletionEvaluationRequest request
    ) {
        return new EvaluateCompletionCommand(
            enrollmentId, request.attendanceRate(), request.completedMinutes(),
            request.creditValue(), request.failureReason());
    }

    public ApiResponse<CompletionResponse> toResponse(Completion completion, String requestId) {
        return new ApiResponse<>(toCompletionResponse(completion), apiMeta(requestId));
    }

    public CompletionResponse toCompletionResponse(Completion completion) {
        return new CompletionResponse(
            completion.id(), completion.enrollmentId(), completion.status(),
            completion.attendanceRate(), completion.completedMinutes(), completion.creditValue(),
            completion.evaluatedAt(), completion.completedAt(), completion.confirmedBy(),
            completion.confirmedAt(), completion.failureReason(), completion.createdAt(),
            completion.updatedAt());
    }

    private ApiMeta apiMeta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
