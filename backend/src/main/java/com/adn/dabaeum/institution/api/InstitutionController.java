package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/institutions")
public class InstitutionController {

    private final InstitutionApplicationService service;
    private final InstitutionApiMapper mapper;
    private final AuthorizationPolicy authorizationPolicy;
    private final CurrentUserProvider currentUserProvider;

    public InstitutionController(
        InstitutionApplicationService service,
        InstitutionApiMapper mapper,
        AuthorizationPolicy authorizationPolicy,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.authorizationPolicy = authorizationPolicy;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InstitutionResponse>> create(
        @Valid @RequestBody InstitutionCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        Institution institution = service.create(
            mapper.toCommand(request), currentUserProvider.requireContext().userId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{institutionId}")
            .buildAndExpand(institution.id())
            .toUri();

        return ResponseEntity.created(location)
            .body(mapper.toApiResponse(
                institution,
                requestId(servletRequest)
            ));
    }

    @GetMapping
    public InstitutionPageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) InstitutionStatus status,
        @RequestParam(required = false) String region,
        HttpServletRequest servletRequest
    ) {
        InstitutionPage result = service.list(
            mapper.toListQuery(page, size, sort, q, status, region)
        );
        return mapper.toPageResponse(
            result,
            requestId(servletRequest)
        );
    }

    @GetMapping("/{institutionId}")
    public ApiResponse<InstitutionResponse> get(
        @PathVariable UUID institutionId,
        HttpServletRequest servletRequest
    ) {
        // 이 화면은 인증만 하면 누구나 연다(강좌 상세가 기관을 보여준다). 관리자 이름·연락처는
        // 그 기관을 관리하는 사람에게만 내려보낸다 — 아니면 로그인만 하면 전 기관 담당자
        // 연락처를 긁을 수 있다.
        boolean manager = authorizationPolicy.isCourseManager(
            currentUserProvider.requireContext(), institutionId);
        return mapper.toDetailApiResponse(
            service.get(institutionId),
            manager ? service.administrators(institutionId) : List.of(),
            requestId(servletRequest)
        );
    }

    @PutMapping("/{institutionId}")
    public ApiResponse<InstitutionResponse> update(
        @PathVariable UUID institutionId,
        @Valid @RequestBody InstitutionUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        var context = currentUserProvider.requireContext();
        if (!authorizationPolicy.isPlatformAdmin(context)) {
            authorizationPolicy.requireInstitutionAdmin(context, institutionId);
            if (!request.hasOnlyInstitutionProfileFields()) {
                throw new com.adn.dabaeum.common.api.ApiException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    com.adn.dabaeum.common.api.ApiErrorCode.FORBIDDEN,
                    "Institution administrators can update contact details, introduction, and logo only"
                );
            }
        }
        return mapper.toApiResponse(
            service.update(mapper.toUpdateCommand(institutionId, request)),
            requestId(servletRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
