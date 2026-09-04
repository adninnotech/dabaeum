package com.adn.dabaeum.institution.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.adn.dabaeum.institution.application.CreateInstitutionCommand;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(InstitutionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=task3-test-token")
@Import({
    InstitutionApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class InstitutionCreateGetControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID INSTITUTION_ID =
        UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InstitutionApplicationService service;

    @BeforeEach
    void setUp() {
        when(service.create(any())).thenReturn(institution());
    }

    @Test
    void createsInstitutionWithLocationAndOpenApiResponse() throws Exception {
        String requestId = "8b706c0e-96cf-4d0a-b357-d80752facf1c";

        MvcResult result = mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionCode": "INST-API-001",
                      "name": "다배움 기관",
                      "businessNumber": "123-45-67890",
                      "representativeName": "홍길동",
                      "address": "서울시 중구",
                      "contactPhone": "02-1234-5678",
                      "contactEmail": "contact@example.com",
                      "status": "ACTIVE"
                    }
                    """)
            )
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/institutions/" + INSTITUTION_ID))
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(jsonPath("$.data.id").value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.institutionCode").value("INST-API-001"))
            .andExpect(jsonPath("$.data.businessNumber").value("123-45-67890"))
            .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(requestId))
            .andExpect(jsonPath("$.meta.timestamp").value(
                matchesPattern(".*\\+09:00$")))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andReturn();

        verify(service).create(any(CreateInstitutionCommand.class));
        org.assertj.core.api.Assertions.assertThat(result.getResponse()
                .getHeader(RequestIdFilter.HEADER_NAME))
            .isEqualTo(requestId);
    }

    @Test
    void passesOmittedStatusAsNullToService() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"institutionCode":"INST-NULL-STATUS","name":"기관"}
                    """))
            .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<CreateInstitutionCommand> captor =
            org.mockito.ArgumentCaptor.forClass(CreateInstitutionCommand.class);
        verify(service).create(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().status())
            .isNull();
    }

    @Test
    void rejectsBlankCreateFieldsWithValidationContract() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"institutionCode":" ","name":"기관"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details").value(
                org.hamcrest.Matchers.hasItem("institutionCode")));
    }

    @Test
    void rejectsOverLengthAndInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionCode": "INST-VALID",
                      "name": "기관",
                      "contactEmail": "not-an-email"
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String longName = "a".repeat(201);
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionCode\":\"INST\",\"name\":\""
                    + longName + "\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsUnknownJsonPropertyAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionCode": "INST-UNKNOWN",
                      "name": "기관",
                      "unknown": "not-allowed"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(service, never()).create(any());
    }

    @Test
    void mapsDuplicateCodeToConflictContract() throws Exception {
        when(service.create(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.INSTITUTION_CODE_CONFLICT,
            "Institution code already exists"
        ));

        mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"institutionCode":"INST-DUP","name":"기관"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("INSTITUTION_CODE_CONFLICT"));
    }

    @Test
    void getsInstitutionByUuid() throws Exception {
        when(service.get(INSTITUTION_ID)).thenReturn(institution());
        String requestId = "7b706c0e-96cf-4d0a-b357-d80752facf1c";

        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(jsonPath("$.data.id").value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.name").value("다배움 기관"))
            .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(requestId))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void rejectsMalformedUuidAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/not-a-uuid")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(service, never()).get(any());
    }

    @Test
    void mapsMissingInstitutionToNotFoundContract() throws Exception {
        when(service.get(INSTITUTION_ID)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.INSTITUTION_NOT_FOUND,
            "Institution not found"
        ));

        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("reader").roles("USER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INSTITUTION_NOT_FOUND"));
    }

    @Test
    void requiresAuthenticationForGet() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsRegularUserForCreate() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("regular-user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionCode\":\"INST\",\"name\":\"기관\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(service, never()).create(any());
    }

    private Institution institution() {
        Instant instant = Instant.parse("2026-08-03T00:00:00Z");
        return new Institution(
            INSTITUTION_ID,
            "INST-API-001",
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
