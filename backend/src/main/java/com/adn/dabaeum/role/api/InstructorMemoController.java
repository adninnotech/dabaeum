package com.adn.dabaeum.role.api;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class InstructorMemoController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserRoleRepository userRoleRepository;
    private final AuthorizationPolicy authorizationPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public InstructorMemoController(
        UserRoleRepository userRoleRepository,
        AuthorizationPolicy authorizationPolicy,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.userRoleRepository = userRoleRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @PatchMapping("/institutions/{institutionId}/instructors/{userId}")
    @Transactional
    public ApiResponse<InstructorMemoResponse> updateMemo(
        @PathVariable UUID institutionId,
        @PathVariable UUID userId,
        @RequestBody(required = false) InstructorMemoUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        authorizationPolicy.requireCourseManager(
            currentUserProvider.requireContext(), institutionId);
        String memo = request == null || request.memo() == null
            ? null : request.memo().trim();
        if (memo != null && (memo.isEmpty() || memo.length() > 1000)) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("memo"));
        }
        if (!userRoleRepository.updateInstructorMemo(institutionId, userId, memo)) {
            throw new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.ROLE_NOT_FOUND,
                "Instructor role not found for the institution");
        }
        return new ApiResponse<>(
            new InstructorMemoResponse(institutionId, userId, memo),
            new ApiMeta(
                (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)));
    }
}
