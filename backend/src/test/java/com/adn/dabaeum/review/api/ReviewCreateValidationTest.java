package com.adn.dabaeum.review.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.adn.dabaeum.review.application.CourseReviewApplicationService;
import com.adn.dabaeum.review.domain.CourseReview;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 수강평 내용은 필수다. 빈 내용·공백만은 422 [content] (QA VAL-03). 등록 후 수정·삭제가 없어 서버가 막는다. */
@WebMvcTest(CourseReviewController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=review-create-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class ReviewCreateValidationTest {

    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID COURSE_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");

    @Autowired MockMvc mockMvc;
    @MockitoBean CourseReviewApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @ParameterizedTest
    @ValueSource(strings = {"{\"rating\": 5}", "{\"rating\": 5, \"content\": null}",
        "{\"rating\": 5, \"content\": \"\"}", "{\"rating\": 5, \"content\": \"   \"}"})
    void rejectsMissingOrBlankContentBeforeTheService(String body) throws Exception {
        mockMvc.perform(post("/api/v1/courses/" + COURSE_ID + "/reviews")
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details[0]").value("content"));
        verifyNoInteractions(service);
    }

    @Test
    void savesTrimmedContent() throws Exception {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        when(currentUserProvider.requireContext()).thenReturn(new AuthenticatedUserContext(
            USER_ID, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", null))));
        when(service.create(any(), eq(COURSE_ID), anyInt(), eq("좋았어요")))
            .thenReturn(new CourseReview(UUID.randomUUID(), USER_ID, COURSE_ID, 5, "좋았어요", now, now));

        mockMvc.perform(post("/api/v1/courses/" + COURSE_ID + "/reviews")
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 5, \"content\": \"  좋았어요  \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.content").value("좋았어요"));
    }
}
