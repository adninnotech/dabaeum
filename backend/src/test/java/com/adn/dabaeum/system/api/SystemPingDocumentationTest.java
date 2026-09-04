package com.adn.dabaeum.system.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemPingController.class)
@AutoConfigureRestDocs(
    uriScheme = "https",
    uriHost = "api.dabaeum.local",
    uriPort = 443
)
@Import({
    SecurityConfiguration.class,
    ClockConfiguration.class,
    RequestIdFilter.class
})
class SystemPingDocumentationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void documentsPingAndValidatesOpenApi() throws Exception {
        String contract = Path.of("docs/api/dabaeum-api-v1.yaml")
            .toAbsolutePath()
            .toUri()
            .toString();

        mockMvc.perform(get("/api/v1/system/ping")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.data.status").value("OK"))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
            .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "system-ping",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("data.status").description("서비스 상태"),
                    fieldWithPath("meta.requestId").description("요청 추적 UUID"),
                    fieldWithPath("meta.timestamp").description("응답 시각")
                )
            ));
    }
}
