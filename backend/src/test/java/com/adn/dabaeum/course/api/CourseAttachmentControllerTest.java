package com.adn.dabaeum.course.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseAttachmentApplicationService;
import com.adn.dabaeum.course.domain.CourseAttachment;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest(CourseAttachmentController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=course-attachment-task3-token")
@Import({
    CourseAttachmentApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseAttachmentControllerTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID ATTACHMENT_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666");
    private static final UUID FILE_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CourseAttachmentApplicationService attachmentService;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void 파일을_강좌에_붙인다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(attachmentService.attach(any(), any())).thenReturn(attachment());

        mockMvc.perform(post("/api/v1/courses/{courseId}/attachments", COURSE_ID)
                .with(user("instructor").roles("INSTRUCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileId\": \"" + FILE_ID + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.fileName").value("강의계획서.pdf"))
            .andExpect(jsonPath("$.data.byteSize").value(251_904));
    }

    @Test
    void 목록은_파일명_크기_등록일을_준다() throws Exception {
        when(attachmentService.list(COURSE_ID)).thenReturn(List.of(attachment()));

        mockMvc.perform(get("/api/v1/courses/{courseId}/attachments", COURSE_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].fileName").value("강의계획서.pdf"))
            .andExpect(jsonPath("$.data[0].byteSize").isNumber())
            .andExpect(jsonPath("$.data[0].createdAt").exists());
    }

    @Test
    void 같은_파일을_두_번_붙이면_409() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(attachmentService.attach(any(), any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.COURSE_ATTACHMENT_CONFLICT, "Already attached"));

        mockMvc.perform(post("/api/v1/courses/{courseId}/attachments", COURSE_ID)
                .with(user("instructor").roles("INSTRUCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileId\": \"" + FILE_ID + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COURSE_ATTACHMENT_CONFLICT"));
    }

    @Test
    void 학습자는_붙일_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/attachments", COURSE_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileId\": \"" + FILE_ID + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 강사는_첨부를_제거한다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(attachmentService.remove(COURSE_ID, ATTACHMENT_ID, instructor()))
            .thenReturn(attachment());

        mockMvc.perform(delete(
                "/api/v1/courses/{courseId}/attachments/{attachmentId}",
                COURSE_ID, ATTACHMENT_ID)
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(
            USER_ID, "LOCAL", Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private CourseAttachment attachment() {
        return new CourseAttachment(
            ATTACHMENT_ID, COURSE_ID, FILE_ID, "강의계획서.pdf", 251_904, 0,
            Instant.parse("2026-08-04T00:00:00Z"));
    }
}
