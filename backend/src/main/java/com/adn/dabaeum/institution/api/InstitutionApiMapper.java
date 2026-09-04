package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.institution.application.CreateInstitutionCommand;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.application.ListInstitutionsQuery;
import com.adn.dabaeum.institution.application.UpdateInstitutionCommand;
import com.adn.dabaeum.institution.domain.Institution;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InstitutionApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public InstitutionApiMapper(Clock clock) {
        this.clock = clock;
    }

    public CreateInstitutionCommand toCommand(InstitutionCreateRequest request) {
        return new CreateInstitutionCommand(
            request.institutionCode(),
            request.name(),
            request.businessNumber(),
            request.representativeName(),
            request.address(),
            request.contactPhone(),
            request.contactEmail(),
            request.status()
        );
    }

    public ListInstitutionsQuery toListQuery(
        int page,
        int size,
        String sort
    ) {
        return new ListInstitutionsQuery(page, size, sort);
    }

    public UpdateInstitutionCommand toUpdateCommand(
        UUID institutionId,
        InstitutionUpdateRequest request
    ) {
        return new UpdateInstitutionCommand(
            institutionId,
            request.institutionCodeUpdate(),
            request.nameUpdate(),
            request.businessNumberUpdate(),
            request.representativeNameUpdate(),
            request.addressUpdate(),
            request.contactPhoneUpdate(),
            request.contactEmailUpdate(),
            request.statusUpdate()
        );
    }

    public ApiResponse<InstitutionResponse> toApiResponse(
        Institution institution,
        String requestId
    ) {
        return new ApiResponse<>(
            toResponse(institution),
            new ApiMeta(
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }

    public InstitutionResponse toResponse(Institution institution) {
        return new InstitutionResponse(
            institution.id(),
            institution.institutionCode(),
            institution.name(),
            institution.businessNumber(),
            institution.representativeName(),
            institution.address(),
            institution.contactPhone(),
            institution.contactEmail(),
            institution.status(),
            institution.createdAt(),
            institution.updatedAt()
        );
    }

    public InstitutionPageResponse toPageResponse(
        InstitutionPage page,
        String requestId
    ) {
        List<InstitutionResponse> data = page.data()
            .stream()
            .map(this::toResponse)
            .toList();
        return new InstitutionPageResponse(
            data,
            new com.adn.dabaeum.common.api.PageMeta(
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
            ),
            new ApiMeta(
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }
}
