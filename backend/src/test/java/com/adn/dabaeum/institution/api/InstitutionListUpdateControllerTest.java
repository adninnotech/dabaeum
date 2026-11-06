package com.adn.dabaeum.institution.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.application.ListInstitutionsQuery;
import com.adn.dabaeum.institution.application.UpdateInstitutionCommand;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InstitutionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=task4-test-token")
@Import({
    InstitutionApiMapper.class,
    com.adn.dabaeum.authorization.application.AuthorizationPolicy.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class InstitutionListUpdateControllerTest {

    private static final UUID INSTITUTION_ID =
        UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InstitutionApplicationService service;

    /** 기관 상세가 관리자 노출 여부를 판단하느라 현재 사용자를 본다. */
    @MockitoBean
    com.adn.dabaeum.common.security.CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUpCurrentUser() {
        when(currentUserProvider.requireContext()).thenReturn(new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "test",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null))
        ));
    }

    @Test
    void listsInstitutionsWithDefaultsAndOpenApiResponse() throws Exception {
        String requestId = "4b706c0e-96cf-4d0a-b357-d80752facf1c";
        when(service.list(any())).thenReturn(page(0, 20, 1L));

        mockMvc.perform(get("/api/v1/institutions")
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(jsonPath("$.data[0].id")
                .value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.page.page").value(0))
            .andExpect(jsonPath("$.page.size").value(20))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.page.totalPages").value(1))
            .andExpect(jsonPath("$.meta.requestId").value(requestId))
            .andExpect(jsonPath("$.meta.timestamp")
                .value(matchesPattern(".*\\+09:00$")));

        ArgumentCaptor<ListInstitutionsQuery> query =
            ArgumentCaptor.forClass(ListInstitutionsQuery.class);
        verify(service).list(query.capture());
        Assertions.assertThat(query.getValue()).isEqualTo(
            new ListInstitutionsQuery(0, 20, "createdAt,desc", null, null, null));
    }

    @Test
    void passesListQueryToApplicationService() throws Exception {
        when(service.list(any())).thenReturn(page(2, 10, 21L));

        mockMvc.perform(get("/api/v1/institutions")
                .queryParam("page", "2")
                .queryParam("size", "10")
                .queryParam("sort", "name,asc")
                .with(user("reader").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.page").value(2))
            .andExpect(jsonPath("$.page.size").value(10))
            .andExpect(jsonPath("$.page.totalPages").value(3));

        ArgumentCaptor<ListInstitutionsQuery> query =
            ArgumentCaptor.forClass(ListInstitutionsQuery.class);
        verify(service).list(query.capture());
        Assertions.assertThat(query.getValue().page()).isEqualTo(2);
        Assertions.assertThat(query.getValue().size()).isEqualTo(10);
        Assertions.assertThat(query.getValue().sort()).isEqualTo("name,asc");
    }

    @Test
    void mapsListApplicationBadRequest() throws Exception {
        when(service.list(any())).thenThrow(new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid"
        ));

        mockMvc.perform(get("/api/v1/institutions")
                .queryParam("sort", "name")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void mapsMalformedListPageParameterToBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/institutions")
                .queryParam("page", "not-a-number")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(service, never()).list(any());
    }

    @Test
    void requiresAuthenticationForList() throws Exception {
        mockMvc.perform(get("/api/v1/institutions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void updatesInstitutionAndValidatesOpenApiResponse() throws Exception {
        String requestId = "5b706c0e-96cf-4d0a-b357-d80752facf1c";
        when(service.update(any())).thenReturn(institution());

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "변경 기관",
                      "businessNumber": null,
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(jsonPath("$.data.id")
                .value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(requestId));

        ArgumentCaptor<UpdateInstitutionCommand> command =
            ArgumentCaptor.forClass(UpdateInstitutionCommand.class);
        verify(service).update(command.capture());
        Assertions.assertThat(command.getValue().name().present()).isTrue();
        Assertions.assertThat(command.getValue().name().value())
            .isEqualTo("변경 기관");
        Assertions.assertThat(command.getValue().businessNumber().present())
            .isTrue();
        Assertions.assertThat(command.getValue().businessNumber().value())
            .isNull();
    }

    @Test
    void preservesAbsentAndExplicitNullUpdateFields() throws Exception {
        when(service.update(any())).thenReturn(institution());

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessNumber":null,"name":"기관"}
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<UpdateInstitutionCommand> command =
            ArgumentCaptor.forClass(UpdateInstitutionCommand.class);
        verify(service).update(command.capture());
        Assertions.assertThat(command.getValue().businessNumber().present())
            .isTrue();
        Assertions.assertThat(command.getValue().businessNumber().value())
            .isNull();
        Assertions.assertThat(command.getValue().address().present()).isFalse();
        Assertions.assertThat(command.getValue().status().present()).isFalse();
    }

    @Test
    void allowsInstitutionAdminToUpdateOnlyOwnContactIntroductionAndLogo() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(institutionAdminContext(INSTITUTION_ID));
        when(service.update(any())).thenReturn(new Institution(
            INSTITUTION_ID, "INST-001", "기관", null, null, null,
            "010-1234-5678", "contact@example.com", InstitutionStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
            null, null, null, UUID.fromString("55555555-5555-5555-5555-555555555555"),
            "기관 소개"));

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("institution-admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contactPhone":"010-1234-5678","introduction":"기관 소개","logoFileId":"55555555-5555-5555-5555-555555555555"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.introduction").value("기관 소개"));

        ArgumentCaptor<UpdateInstitutionCommand> command =
            ArgumentCaptor.forClass(UpdateInstitutionCommand.class);
        verify(service).update(command.capture());
        Assertions.assertThat(command.getValue().contactPhone().value()).isEqualTo("010-1234-5678");
        Assertions.assertThat(command.getValue().introduction().value()).isEqualTo("기관 소개");
        Assertions.assertThat(command.getValue().logoFileId().present()).isTrue();
    }

    @Test
    void rejectsInstitutionAdminUpdatesToInstitutionNameAndCode() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(institutionAdminContext(INSTITUTION_ID));

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("institution-admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"바뀐 이름\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("institution-admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionCode\":\"CHANGED\"}"))
            .andExpect(status().isForbidden());
        verify(service, never()).update(any());
    }

    @Test
    void rejectsInstitutionAdminUpdatesToAnotherInstitution() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(institutionAdminContext(INSTITUTION_ID));
        UUID otherInstitution = UUID.fromString("22222222-2222-2222-2222-222222222222");

        mockMvc.perform(put("/api/v1/institutions/{id}", otherInstitution)
                .with(user("institution-admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactPhone\":\"010-1234-5678\"}"))
            .andExpect(status().isForbidden());
        verify(service, never()).update(any());
    }

    @Test
    void mapsEmptyUpdateToValidationError() throws Exception {
        when(service.update(any())).thenThrow(new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed"
        ));

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsUpdateValidationAndInvalidStatus() throws Exception {
        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactEmail\":\"invalid\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNKNOWN\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(service, never()).update(any());
    }

    @Test
    void mapsUpdateNotFoundAndConflictWithRequestId() throws Exception {
        String requestId = "6b706c0e-96cf-4d0a-b357-d80752facf1c";
        when(service.update(any())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.INSTITUTION_NOT_FOUND,
            "Institution not found"
        ));

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"기관\"}"))
            .andExpect(status().isNotFound())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(jsonPath("$.code").value("INSTITUTION_NOT_FOUND"))
            .andExpect(jsonPath("$.requestId").value(requestId));

        reset(service);
        when(service.update(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.INSTITUTION_CODE_CONFLICT,
            "Institution code already exists"
        ));
        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionCode\":\"DUP\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("INSTITUTION_CODE_CONFLICT"));
    }

    @Test
    void mapsMalformedUpdateUuidToBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/institutions/not-a-uuid")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"기관\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(service, never()).update(any());
    }

    @Test
    void requiresAuthenticationAndPlatformAdminForUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"기관\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(put("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("regular-user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"기관\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(service, never()).update(any());
    }

    private InstitutionPage page(int page, int size, long totalElements) {
        return new InstitutionPage(
            List.of(new com.adn.dabaeum.institution.domain.InstitutionListItem(
                institution(), 0, 0, 0, 0, null)),
            page,
            size,
            totalElements,
            totalElements == 0
                ? 0
                : (int) (((totalElements - 1) / size) + 1)
        );
    }

    private AuthenticatedUserContext institutionAdminContext(UUID institutionId) {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "test",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId))
        );
    }

    private Institution institution() {
        Instant instant = Instant.parse("2026-08-03T00:00:00Z");
        return new Institution(
            INSTITUTION_ID,
            "INST-API-004",
            "다배움 기관",
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "contact@example.com",
            InstitutionStatus.ACTIVE,
            instant,
            instant,
            null
        );
    }
}
