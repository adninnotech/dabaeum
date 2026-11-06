package com.adn.dabaeum.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.config.FileStorageProperties;
import com.adn.dabaeum.file.domain.FileStoragePort;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

/**
 * 업로드가 받아주는 파일 종류는 purpose 마다 다르다. 첨부파일은 문서를 받아야 하고
 * 나머지(프로필·썸네일·로고·배너)는 이미지만 받는다. 이 규칙이 틀어지면 화면이 통째로 막힌다 —
 * 공지 첨부가 PDF 를 못 받던 회귀가 실제로 있었다.
 */
@ExtendWith(MockitoExtension.class)
class FileUploadPurposePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0
    };
    private static final byte[] JPEG = {
        (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0
    };
    private static final byte[] WEBP = {
        'R', 'I', 'F', 'F', 16, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
    };
    private static final byte[] PDF = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Mock StoredFileRepository fileRepository;
    @Mock FileStoragePort storage;
    @Mock FileAccessPolicy accessPolicy;

    private DefaultFileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultFileApplicationService(
            fileRepository, storage, new FileStorageProperties("files", DataSize.ofMegabytes(10)),
            accessPolicy);
        lenient().when(storage.store(any(), any(), any())).thenReturn("files/stored");
    }

    @Test
    void 문의_목적의_새_업로드는_거부한다() {
        assertThatThrownBy(() -> service.upload(png(StoredFilePurpose.INQUIRY)))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).code())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 문서를_받아야_하는_purpose() {
        for (StoredFilePurpose purpose : Set.of(
                StoredFilePurpose.COURSE_ATTACHMENT,
                StoredFilePurpose.NOTICE_ATTACHMENT)) {
            StoredFile stored = service.upload(pdf(purpose));
            assertThat(stored.purpose()).isEqualTo(purpose);
        }
    }

    @Test
    void 이미지만_받는_purpose_에_PDF_를_주면_422() {
        for (StoredFilePurpose purpose : Set.of(
                StoredFilePurpose.PROFILE,
                StoredFilePurpose.COURSE_THUMBNAIL,
                StoredFilePurpose.INSTITUTION_LOGO,
                StoredFilePurpose.BANNER)) {
            assertThatThrownBy(() -> service.upload(pdf(purpose)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code())
                    .isEqualTo(ApiErrorCode.FILE_TYPE_NOT_SUPPORTED));
        }
    }

    @Test
    void 모든_purpose_가_이미지는_받는다() {
        for (StoredFilePurpose purpose : StoredFilePurpose.values()) {
            if (purpose == StoredFilePurpose.INQUIRY) continue;
            StoredFile stored = service.upload(png(purpose));
            assertThat(stored.purpose()).isEqualTo(purpose);
        }
    }

    @Test
    void forgedContentTypeDoesNotStoreBytesOrMetadata() {
        byte[] arbitrary = "not an image or pdf".getBytes(StandardCharsets.US_ASCII);
        for (String type : Set.of("image/jpeg", "image/png", "image/webp", "application/pdf")) {
            assertThatThrownBy(() -> service.upload(command(
                StoredFilePurpose.COURSE_ATTACHMENT, type, "fake.bin", arbitrary)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                    .isEqualTo(ApiErrorCode.FILE_TYPE_NOT_SUPPORTED));
        }
        verifyNoInteractions(storage, fileRepository);
    }

    @Test
    void declaredTypeMustMatchFileSignature() {
        assertThatThrownBy(() -> service.upload(command(
            StoredFilePurpose.COURSE_ATTACHMENT, "image/png", "photo.png", JPEG)))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.FILE_TYPE_NOT_SUPPORTED));
        verifyNoInteractions(storage, fileRepository);
    }

    @Test
    void recognizedFileBytesReachStorageWithoutLosingSignature() {
        for (var sample : Map.of(
            "image/jpeg", JPEG,
            "image/png", PNG,
            "image/webp", WEBP,
            "application/pdf", PDF).entrySet()) {
            byte[] bytes = sample.getValue();
            doAnswer(invocation -> {
                assertThat(((InputStream) invocation.getArgument(2)).readAllBytes())
                    .isEqualTo(bytes);
                return "files/stored";
            }).when(storage).store(any(), any(), any());
            StoredFile stored = service.upload(command(
                StoredFilePurpose.COURSE_ATTACHMENT, sample.getKey(), "file", bytes));
            assertThat(stored.contentType()).isEqualTo(sample.getKey());
            assertThat(stored.size()).isEqualTo(bytes.length);
        }
    }

    private UploadFileCommand pdf(StoredFilePurpose purpose) {
        return command(purpose, "application/pdf", "안내문.pdf", PDF);
    }

    private UploadFileCommand png(StoredFilePurpose purpose) {
        return command(purpose, "image/png", "그림.png", PNG);
    }

    private UploadFileCommand command(
        StoredFilePurpose purpose, String contentType, String name, byte[] bytes
    ) {
        InputStream content = new ByteArrayInputStream(bytes);
        return new UploadFileCommand(
            purpose.name(), name, contentType, bytes.length, content, actor(), NOW);
    }

    private AuthenticatedUserContext actor() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
