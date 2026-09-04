package com.adn.dabaeum.identity.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.identity.application.LinkIdentityCommand;
import com.adn.dabaeum.identity.domain.UserIdentity;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public IdentityApiMapper(Clock clock) {
        this.clock = clock;
    }

    public LinkIdentityCommand toCommand(
        UUID userId,
        IdentityLinkRequest request
    ) {
        return new LinkIdentityCommand(
            userId,
            request.provider(),
            request.providerSubject(),
            request.externalDid(),
            request.verified()
        );
    }

    public ApiResponse<List<IdentityResponse>> toListResponse(
        List<UserIdentity> identities,
        String requestId
    ) {
        return new ApiResponse<>(
            identities.stream().map(this::toResponse).toList(),
            apiMeta(requestId)
        );
    }

    public ApiResponse<IdentityResponse> toResponse(
        UserIdentity identity,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(identity), apiMeta(requestId));
    }

    public IdentityResponse toResponse(UserIdentity identity) {
        return new IdentityResponse(
            identity.id(),
            identity.provider(),
            identity.verifiedAt(),
            identity.createdAt(),
            identity.updatedAt()
        );
    }

    private ApiMeta apiMeta(String requestId) {
        return new ApiMeta(
            requestId,
            OffsetDateTime.ofInstant(clock.instant(), SEOUL)
        );
    }
}
