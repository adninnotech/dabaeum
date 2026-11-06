package com.adn.dabaeum.support.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeStatus;
import com.adn.dabaeum.support.domain.NoticeAttachment;
import com.adn.dabaeum.support.domain.NoticeAttachmentRepository;
import com.adn.dabaeum.support.domain.NoticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** course.application.DefaultCourseAttachmentApplicationService 와 같은 모양이다. */
@Service
@Profile({"local", "dev"})
public class DefaultNoticeAttachmentApplicationService
    implements NoticeAttachmentApplicationService {

    private final NoticeAttachmentRepository attachmentRepository;
    private final NoticeRepository noticeRepository;
    private final StoredFileRepository fileRepository;
    private final NoticeAttachmentIdGenerator idGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultNoticeAttachmentApplicationService(
        NoticeAttachmentRepository attachmentRepository,
        NoticeRepository noticeRepository,
        StoredFileRepository fileRepository,
        NoticeAttachmentIdGenerator idGenerator,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.attachmentRepository = Objects.requireNonNull(attachmentRepository);
        this.noticeRepository = Objects.requireNonNull(noticeRepository);
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeAttachment> list(UUID noticeId) {
        findNotice(noticeId);
        return attachmentRepository.findActiveByNoticeId(noticeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeAttachment> listPublished(UUID noticeId) {
        Notice notice = findNotice(noticeId);
        // DefaultSupportApplicationService.getPublishedNotice 와 같은 기준. 예약 게시분은 아직 안 보인다.
        if (notice.status() != NoticeStatus.PUBLISHED
            || (notice.publishedAt() != null && notice.publishedAt().isAfter(clock.instant()))) {
            throw noticeNotFound();
        }
        return attachmentRepository.findActiveByNoticeId(noticeId);
    }

    @Override
    @Transactional
    public NoticeAttachment attach(
        AttachNoticeAttachmentCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.noticeId() == null || command.fileId() == null) {
            throw validation("fileId");
        }
        findNotice(command.noticeId());
        authorizationPolicy.requirePlatformAdmin(context);
        StoredFile file = fileRepository.findById(command.fileId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.FILE_NOT_FOUND, "File not found"));
        if (file.purpose() != StoredFilePurpose.NOTICE_ATTACHMENT) {
            throw validation("fileId");
        }

        Instant now = clock.instant();
        NoticeAttachment attachment = new NoticeAttachment(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            command.noticeId(), file.id(), file.originalName(), file.size(), 0, now);
        try {
            attachmentRepository.save(attachment);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return attachment;
    }

    @Override
    @Transactional
    public NoticeAttachment remove(UUID noticeId, UUID attachmentId, AuthenticatedUserContext context) {
        if (noticeId == null || attachmentId == null) {
            throw validation("attachmentId");
        }
        NoticeAttachment existing = attachmentRepository.findActiveById(attachmentId)
            .filter(attachment -> attachment.noticeId().equals(noticeId))
            .orElseThrow(this::notFound);
        findNotice(noticeId);
        authorizationPolicy.requirePlatformAdmin(context);
        if (!attachmentRepository.softDelete(existing.id(), clock.instant())) {
            throw notFound();
        }
        return existing;
    }

    private Notice findNotice(UUID noticeId) {
        if (noticeId == null) {
            throw validation("noticeId");
        }
        return noticeRepository.findById(noticeId).orElseThrow(this::noticeNotFound);
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }

    private ApiException noticeNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.NOTICE_NOT_FOUND, "Notice not found");
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.NOTICE_ATTACHMENT_NOT_FOUND,
            "Notice attachment not found");
    }

    private ApiException conflict() {
        return new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.NOTICE_ATTACHMENT_CONFLICT,
            "Notice attachment already exists");
    }
}
