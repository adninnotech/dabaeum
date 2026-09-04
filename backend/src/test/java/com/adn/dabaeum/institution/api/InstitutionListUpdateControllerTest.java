package com.adn.dabaeum.institution.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
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
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.application.ListInstitutionsQuery;
import com.adn.dabaeum.institution.application.UpdateInstitutionCommand;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
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
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class InstitutionListUpdateControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID INSTITUTION_ID =
        UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InstitutionApplicationService service;

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
                .value(matchesPattern(".*\\+09:00$")))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        ArgumentCaptor<ListInstitutionsQuery> query =
            ArgumentCaptor.forClass(ListInstitutionsQuery.class);
        verify(service).list(query.capture());
        Assertions.assertThat(query.getValue()).isEqualTo(
            new ListInstitutionsQuery(0, 20, "createdAt,desc"));
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
            .andExpect(jsonPath("$.meta.requestId").value(requestId))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

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
    void mapsEmptyUpdateToValidationError() throws Exception {
        when(service.update(any())).thenThrow(new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
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
            List.of(institution()),
            page,
            size,
            totalElements,
            totalElements == 0
                ? 0
                : (int) (((totalElements - 1) / size) + 1)
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
