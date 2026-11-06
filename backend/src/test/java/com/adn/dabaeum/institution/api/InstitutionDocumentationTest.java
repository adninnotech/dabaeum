package com.adn.dabaeum.institution.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionListItem;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InstitutionController.class)
@AutoConfigureRestDocs(
    uriScheme = "https",
    uriHost = "api.dabaeum.local",
    uriPort = 443
)
@Import({
    InstitutionApiMapper.class,
    com.adn.dabaeum.authorization.application.AuthorizationPolicy.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class InstitutionDocumentationTest {

    private static final UUID INSTITUTION_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666"
    );
    private static final String REQUEST_ID =
        "7b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InstitutionApplicationService service;

    /** 기관 상세가 관리자 노출 여부를 판단하느라 현재 사용자를 본다. */
    @MockitoBean
    com.adn.dabaeum.common.security.CurrentUserProvider currentUserProvider;

    @org.junit.jupiter.api.BeforeEach
    void stubPlatformAdmin() {
        org.mockito.Mockito.when(currentUserProvider.requireContext()).thenReturn(
            new com.adn.dabaeum.common.security.AuthenticatedUserContext(
                java.util.UUID.fromString("ffffffff-0000-0000-0000-000000000001"), "LOCAL",
                java.util.Set.of(new com.adn.dabaeum.common.security.AuthenticatedRole(
                    "PLATFORM_ADMIN", null))));
    }

    @Test
    void documentsInstitutionList() throws Exception {
        when(service.list(any())).thenReturn(new InstitutionPage(
            List.of(new InstitutionListItem(institution(), 0, 0, 0, 0, null)),
            0,
            20,
            1,
            1
        ));

        mockMvc.perform(get("/api/v1/institutions")
                .queryParam("page", "0")
                .queryParam("size", "20")
                .queryParam("sort", "createdAt,desc")
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data[0].institutionCode")
                .value("INST-DOC-001"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.page.totalPages").value(1))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andDo(document(
                "institution-list",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                queryParameters(
                    parameterWithName("page").description("0부터 시작하는 페이지"),
                    parameterWithName("size").description("페이지 크기"),
                    parameterWithName("sort").description("정렬 필드와 방향")
                ),
                institutionResponseFields(true)
            ));
    }

    @Test
    void documentsInstitutionCreate() throws Exception {
        when(service.create(any(), any())).thenReturn(institution());

        mockMvc.perform(post("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionCode": "INST-DOC-001",
                      "name": "다배움 문서 기관",
                      "businessNumber": "123-45-67890",
                      "representativeName": "홍길동",
                      "address": "서울시 중구",
                      "contactPhone": "02-1234-5678",
                      "contactEmail": "contact@example.com",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                "Location",
                "https://api.dabaeum.local/api/v1/institutions/" + INSTITUTION_ID
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.name").value("다배움 문서 기관"))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andDo(document(
                "institution-create",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("institutionCode").description("기관 코드"),
                    fieldWithPath("name").description("기관명"),
                    fieldWithPath("businessNumber").description("사업자번호"),
                    fieldWithPath("representativeName")
                        .description("대표자명"),
                    fieldWithPath("address").description("주소"),
                    fieldWithPath("contactPhone").description("연락처"),
                    fieldWithPath("contactEmail").description("이메일"),
                    fieldWithPath("status").description("기관 상태")
                ),
                institutionResponseFields(false)
            ));
    }

    @Test
    void documentsInstitutionGet() throws Exception {
        when(service.get(INSTITUTION_ID)).thenReturn(institution());

        mockMvc.perform(get("/api/v1/institutions/{institutionId}", INSTITUTION_ID)
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andDo(document(
                "institution-get",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                institutionResponseFields(false)
            ));
    }

    @Test
    void documentsInstitutionUpdate() throws Exception {
        Institution updated = new Institution(
            INSTITUTION_ID,
            "INST-DOC-001",
            "다배움 수정 기관",
            null,
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "contact@example.com",
            InstitutionStatus.SUSPENDED,
            institution().createdAt(),
            Instant.parse("2026-08-03T01:00:00Z"),
            null
        );
        when(service.update(any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/institutions/{institutionId}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "다배움 수정 기관",
                      "businessNumber": null,
                      "status": "SUSPENDED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.name").value("다배움 수정 기관"))
            .andExpect(jsonPath("$.data.businessNumber").doesNotExist())
            .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andDo(document(
                "institution-update",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("name").description("기관명"),
                    fieldWithPath("businessNumber")
                        .description("null 지정 시 값을 지움"),
                    fieldWithPath("status").description("기관 상태")
                ),
                institutionResponseFields(false)
            ));
    }

    private ResponseFieldsSnippet institutionResponseFields(boolean page) {
        if (page) {
            return responseFields(
                fieldWithPath("data[].id").description("기관 ID"),
                fieldWithPath("data[].institutionCode").description("기관 코드"),
                fieldWithPath("data[].name").description("기관명"),
                fieldWithPath("data[].businessNumber").description("사업자번호"),
                fieldWithPath("data[].representativeName").description("대표자명"),
                fieldWithPath("data[].address").description("주소"),
                fieldWithPath("data[].contactPhone").description("연락처"),
                fieldWithPath("data[].contactEmail").description("이메일"),
                fieldWithPath("data[].status").description("기관 상태"),
                fieldWithPath("data[].createdAt").description("생성 시각"),
                fieldWithPath("data[].updatedAt").description("수정 시각"),
                fieldWithPath("data[].homepageUrl").description("홈페이지 URL"),
                fieldWithPath("data[].region").description("지역"),
                fieldWithPath("data[].logoFileId").description("로고 파일 ID"),
                fieldWithPath("data[].introduction").description("기관 소개"),
                fieldWithPath("data[].instructorCount").description("소속 강사 수"),
                fieldWithPath("data[].courseCount").description("개설 과정 수(삭제 제외)"),
                fieldWithPath("data[].learnerCount").description("승인 수강 학습자 수(중복 제외)"),
                fieldWithPath("data[].adminCount").description("기관 관리자 계정 수"),
                fieldWithPath("page.page").description("현재 페이지"),
                fieldWithPath("page.size").description("페이지 크기"),
                fieldWithPath("page.totalElements").description("전체 요소 수"),
                fieldWithPath("page.totalPages").description("전체 페이지 수"),
                fieldWithPath("meta.requestId").description("요청 추적 UUID"),
                fieldWithPath("meta.timestamp").description("응답 시각")
            );
        }
        return responseFields(
            fieldWithPath("data.id").description("기관 ID"),
            fieldWithPath("data.institutionCode").description("기관 코드"),
            fieldWithPath("data.name").description("기관명"),
            fieldWithPath("data.businessNumber").description("사업자번호"),
            fieldWithPath("data.representativeName").description("대표자명"),
            fieldWithPath("data.address").description("주소"),
            fieldWithPath("data.contactPhone").description("연락처"),
            fieldWithPath("data.contactEmail").description("이메일"),
            fieldWithPath("data.status").description("기관 상태"),
            fieldWithPath("data.createdAt").description("생성 시각"),
            fieldWithPath("data.updatedAt").description("수정 시각"),
            fieldWithPath("data.homepageUrl").description("홈페이지 URL"),
            fieldWithPath("data.region").description("지역"),
            fieldWithPath("data.logoFileId").description("로고 파일 ID"),
            fieldWithPath("data.introduction").description("기관 소개"),
            subsectionWithPath("data.administrators")
                .type(org.springframework.restdocs.payload.JsonFieldType.ARRAY)
                .description("기관 관리자 목록. 상세 조회에서만 온다.")
                .optional(),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        );
    }

    private Institution institution() {
        Instant instant = Instant.parse("2026-08-03T00:00:00Z");
        return new Institution(
            INSTITUTION_ID,
            "INST-DOC-001",
            "다배움 문서 기관",
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
