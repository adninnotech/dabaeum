package com.adn.dabaeum.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.user.application.LearnerLookupService;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 중앙 #56 — 기관 관리자가 대리 수강신청할 학습자를 이름·생년월일·휴대전화 정확 일치로 찾는다. */
@WebMvcTest(LearnerLookupController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=learner-lookup-test-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class LearnerLookupControllerTest {

    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LEARNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PATH = "/api/v1/institutions/{institutionId}/learners/lookup";

    @Autowired MockMvc mockMvc;
    @MockitoBean LearnerLookupService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void returnsOnlyIdAndNameWhenAllValuesMatch() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.lookup(any(), eq(INSTITUTION_ID), eq("홍길동"), eq("1990-01-15"), eq("01012345678")))
            .thenReturn(learner(LocalDate.of(1990, 1, 15)));

        mockMvc.perform(lookup("홍길동", "1990-01-15", "01012345678")
                .with(user("admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(LEARNER_ID.toString()))
            .andExpect(jsonPath("$.data.name").value("홍길동"))
            // 관리자가 이미 들은 값과 학번은 내려주지 않는다.
            .andExpect(jsonPath("$.data.learnerNo").doesNotExist())
            .andExpect(jsonPath("$.data.birthDate").doesNotExist())
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    @Test
    void serviceErrorsKeepTheirStatus() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.lookup(any(), any(), any(), any(), any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
            ApiErrorCode.USER_NOT_FOUND, "Learner not found"));

        mockMvc.perform(lookup("홍길동", "1990-01-15", "01012345678")
                .with(user("admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void learnersAndAnonymousCannotCall() throws Exception {
        mockMvc.perform(lookup("홍길동", "1990-01-15", "01012345678")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(lookup("홍길동", "1990-01-15", "01012345678"))
            .andExpect(status().isUnauthorized());
        verify(service, never()).lookup(any(), any(), any(), any(), any());
    }

    @Test
    void removedFieldsAreRejectedBeforeLookup() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());

        mockMvc.perform(post(PATH, INSTITUTION_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerNo\":\"262700123\",\"birthDate\":\"1990-01-15\"}")
                .with(user("admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isBadRequest());
        verify(service, never()).lookup(any(), any(), any(), any(), any());
    }

    private static MockHttpServletRequestBuilder lookup(String name, String birthDate, String phone) {
        return post(PATH, INSTITUTION_ID).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":" + quoted(name) + ",\"birthDate\":" + quoted(birthDate)
                + ",\"phone\":" + quoted(phone) + "}");
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private static User learner(LocalDate birthDate) {
        Instant now = Instant.parse("2026-10-26T00:00:00Z");
        return new User(LEARNER_ID, "홍길동", null, "010-1234-5678", birthDate, UserStatus.ACTIVE, null, now, now,
            null, null, null, null, null,
            User.DEFAULT_EMAIL_NOTIFICATION_ENABLED, User.DEFAULT_SMS_NOTIFICATION_ENABLED,
            null);
    }
}
