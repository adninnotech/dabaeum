package com.adn.dabaeum.authentication.api;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
@AutoConfigureRestDocs(
    uriScheme = "https",
    uriHost = "api.dabaeum.local",
    uriPort = 443
)
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class SessionDocumentationTest {

    private static final UUID USER_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-04T01:00:00Z");
    private static final String REQUEST_ID =
        "7b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @Test
    void documentsSessionContract() throws Exception {
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            USER_ID,
            "DADAEGU",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID))
        );
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
            USER_ID,
            "DADAEGU",
            Set.of("INSTRUCTOR")
        );
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                Set.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))
            );
        authentication.setDetails(new AuthenticatedTokenDetails(
            EXPIRES_AT,
            context
        ));

        mockMvc.perform(get("/api/v1/auth/session")
                .with(authentication(authentication))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andDo(document(
                "auth-session",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                responseFields(
                    fieldWithPath("data.userId").description("사용자 UUID"),
                    fieldWithPath("data.provider").description("인증 Provider"),
                    fieldWithPath("data.roles[].role").description("역할"),
                    fieldWithPath("data.roles[].institutionId")
                        .description("기관 범위 UUID"),
                    fieldWithPath("data.expiresAt")
                        .description("인증 만료 시각"),
                    fieldWithPath("meta.requestId")
                        .description("요청 추적 UUID"),
                    fieldWithPath("meta.timestamp")
                        .description("응답 시각")
                )
            ));
    }
}
