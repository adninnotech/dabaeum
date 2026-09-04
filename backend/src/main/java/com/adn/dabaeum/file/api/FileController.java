package com.adn.dabaeum.file.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.file.application.FileApplicationService;
import com.adn.dabaeum.file.application.FileDownload;
import com.adn.dabaeum.file.application.UploadFileCommand;
import com.adn.dabaeum.file.domain.StoredFile;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class FileController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FileApplicationService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public FileController(
        FileApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoredFileResponse>> upload(
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam(value = "purpose", required = false) String purpose,
        HttpServletRequest servletRequest
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("file"));
        }
        StoredFile stored;
        try (var content = file.getInputStream()) {
            stored = service.upload(new UploadFileCommand(
                purpose, file.getOriginalFilename(), file.getContentType(),
                file.getSize(), content, currentUserProvider.requireContext(),
                clock.instant()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create(contentUrl(stored.id())))
            .body(new ApiResponse<>(toResponse(stored), meta(requestId(servletRequest))));
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID fileId) {
        FileDownload download = service.download(fileId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(download.file().contentType()))
            .contentLength(download.file().size())
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
            .body(new InputStreamResource(download.content()));
    }

    static String contentUrl(UUID fileId) {
        return "/api/v1/files/" + fileId + "/content";
    }

    private StoredFileResponse toResponse(StoredFile file) {
        return new StoredFileResponse(
            file.id(), contentUrl(file.id()), file.purpose(), file.originalName(),
            file.contentType(), file.size(), file.createdAt());
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
