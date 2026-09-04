package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.domain.Institution;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
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

    public InstitutionController(
        InstitutionApplicationService service,
        InstitutionApiMapper mapper
    ) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InstitutionResponse>> create(
        @Valid @RequestBody InstitutionCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        Institution institution = service.create(mapper.toCommand(request));
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
        HttpServletRequest servletRequest
    ) {
        InstitutionPage result = service.list(
            mapper.toListQuery(page, size, sort)
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
        return mapper.toApiResponse(
            service.get(institutionId),
            requestId(servletRequest)
        );
    }

    @PutMapping("/{institutionId}")
    public ApiResponse<InstitutionResponse> update(
        @PathVariable UUID institutionId,
        @Valid @RequestBody InstitutionUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.update(mapper.toUpdateCommand(institutionId, request)),
            requestId(servletRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
