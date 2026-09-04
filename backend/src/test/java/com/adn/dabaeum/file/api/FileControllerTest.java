package com.adn.dabaeum.file.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.file.application.FileApplicationService;
import com.adn.dabaeum.file.application.FileDownload;
import com.adn.dabaeum.file.application.UploadFileCommand;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FileController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=file-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class FileControllerTest {

    private static final UUID FILE_ID = UUID.fromString(
        "a0000000-0000-0000-0000-00000000000a");
    private static final String REQUEST_ID = "11111111-2222-3333-4444-555555555555";
    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final byte[] PNG = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mockMvc;
    @MockitoBean FileApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void uploadsMultipartFileAndReturnsContentUrl() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.upload(any(UploadFileCommand.class))).thenReturn(storedFile());

        mockMvc.perform(multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
                .param("purpose", "PROFILE")
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/files/" + FILE_ID + "/content"))
            .andExpect(jsonPath("$.data.id").value(FILE_ID.toString()))
            .andExpect(jsonPath("$.data.url").value("/api/v1/files/" + FILE_ID + "/content"))
            .andExpect(jsonPath("$.data.purpose").value("PROFILE"))
            .andExpect(jsonPath("$.data.contentType").value("image/png"))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));

        ArgumentCaptor<UploadFileCommand> command =
            ArgumentCaptor.forClass(UploadFileCommand.class);
        verify(service).upload(command.capture());
        assertThat(command.getValue().purpose()).isEqualTo("PROFILE");
        assertThat(command.getValue().originalName()).isEqualTo("photo.png");
        assertThat(command.getValue().contentType()).isEqualTo("image/png");
        assertThat(command.getValue().size()).isEqualTo(PNG.length);
        assertThat(command.getValue().actor().userId()).isEqualTo(USER_ID);
    }

    @Test
    void rejectsEmptyUploadWithValidationError() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());

        mockMvc.perform(multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                .param("purpose", "PROFILE")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details[0]").value("file"));
    }

    @Test
    void propagatesUnsupportedTypeFromService() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.upload(any(UploadFileCommand.class))).thenThrow(new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.FILE_TYPE_NOT_SUPPORTED,
            "File type is not supported", List.of("file")));

        mockMvc.perform(multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "x.exe", "application/octet-stream", PNG))
                .param("purpose", "PROFILE")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_SUPPORTED"));
    }

    @Test
    void rejectsUnauthenticatedUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
                .param("purpose", "PROFILE"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void servesContentPubliclyWithStoredTypeAndCacheHeaders() throws Exception {
        when(service.download(FILE_ID)).thenReturn(
            new FileDownload(storedFile(), new ByteArrayInputStream(PNG)));

        mockMvc.perform(get("/api/v1/files/{id}/content", FILE_ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string("Content-Length", String.valueOf(PNG.length)))
            .andExpect(header().string("Cache-Control", "max-age=86400, public"))
            .andExpect(content().bytes(PNG));
    }

    @Test
    void returnsNotFoundForUnknownFile() throws Exception {
        when(service.download(FILE_ID)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.FILE_NOT_FOUND, "File not found"));

        mockMvc.perform(get("/api/v1/files/{id}/content", FILE_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    private StoredFile storedFile() {
        return new StoredFile(
            FILE_ID, StoredFilePurpose.PROFILE, "photo.png", "image/png", PNG.length,
            USER_ID + "/image/" + FILE_ID + ".png", USER_ID,
            Instant.parse("2026-08-28T00:00:00Z"));
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
