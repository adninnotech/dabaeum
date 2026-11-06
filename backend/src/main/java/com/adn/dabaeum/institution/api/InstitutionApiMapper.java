package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.institution.application.CreateInstitutionCommand;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.institution.application.ListInstitutionsQuery;
import com.adn.dabaeum.institution.application.UpdateInstitutionCommand;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionAdministrator;
import com.adn.dabaeum.institution.domain.InstitutionListItem;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
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
            request.status(),
            request.homepageUrl(),
            request.region(),
            request.logoFileId()
        );
    }

    public ListInstitutionsQuery toListQuery(
        int page,
        int size,
        String sort,
        String q,
        InstitutionStatus status,
        String region
    ) {
        return new ListInstitutionsQuery(
            page, size, sort, KeywordQuery.normalize(q), status, normalizeRegion(region));
    }

    // region 은 셀렉트 값과의 정확 일치라 LIKE 이스케이프·소문자 변환은 필요 없다. trim 만 한다.
    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return region.trim();
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
            request.statusUpdate(),
            request.homepageUrlUpdate(),
            request.regionUpdate(),
            request.logoFileIdUpdate(),
            request.introductionUpdate()
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

    /** 상세 조회 전용. administrators 를 채운다 — 목록은 이 메서드를 쓰지 않는다. */
    public ApiResponse<InstitutionResponse> toDetailApiResponse(
        Institution institution,
        List<InstitutionAdministrator> administrators,
        String requestId
    ) {
        return new ApiResponse<>(
            toResponse(institution, administrators),
            new ApiMeta(
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }

    public InstitutionResponse toResponse(Institution institution) {
        return toResponse(institution, null);
    }

    private InstitutionResponse toResponse(
        Institution institution,
        List<InstitutionAdministrator> administrators
    ) {
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
            institution.updatedAt(),
            institution.homepageUrl(),
            institution.region(),
            institution.logoFileId(),
            institution.introduction(),
            administrators,
            null,
            null,
            null,
            null,
            null
        );
    }

    /** 목록 전용. administrators 는 항상 없고, 집계 4종과 대표 관리자 이름만 채운다. */
    private InstitutionResponse toListResponse(InstitutionListItem item) {
        Institution institution = item.institution();
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
            institution.updatedAt(),
            institution.homepageUrl(),
            institution.region(),
            institution.logoFileId(),
            institution.introduction(),
            null,
            item.instructorCount(),
            item.courseCount(),
            item.learnerCount(),
            item.adminCount(),
            item.primaryAdminName()
        );
    }

    public InstitutionPageResponse toPageResponse(
        InstitutionPage page,
        String requestId
    ) {
        List<InstitutionResponse> data = page.data()
            .stream()
            .map(this::toListResponse)
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
