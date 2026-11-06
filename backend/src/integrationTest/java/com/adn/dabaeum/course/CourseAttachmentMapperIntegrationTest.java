package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseAttachment;
import com.adn.dabaeum.course.domain.CourseAttachmentRepository;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseAttachmentMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CourseAttachmentRepository attachmentRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StoredFileRepository fileRepository;

    @Test
    void savesReadsWithJoinedFileMetadataAndAllowsReattachAfterSoftDelete() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Institution institution = institution();
        institutionRepository.save(institution);
        userRepository.save(user(userId));
        courseRepository.save(course(courseId, institution.id()));

        StoredFile file = storedFile(userId);
        fileRepository.save(file);

        Instant createdAt = Instant.parse("2026-08-04T00:00:00Z");
        CourseAttachment attachment = new CourseAttachment(
            UUID.randomUUID(), courseId, file.id(), "무시되는_이름", 1, 0, createdAt);
        attachmentRepository.save(attachment);

        assertThat(attachmentRepository.findActiveByCourseId(courseId))
            .hasSize(1)
            .first()
            .satisfies(found -> {
                assertThat(found.id()).isEqualTo(attachment.id());
                assertThat(found.fileName()).isEqualTo(file.originalName());
                assertThat(found.byteSize()).isEqualTo(file.size());
            });
        assertThat(attachmentRepository.findActiveById(attachment.id())).isPresent();

        assertThat(attachmentRepository.softDelete(attachment.id(), Instant.now())).isTrue();
        assertThat(attachmentRepository.findActiveByCourseId(courseId)).isEmpty();
        assertThat(attachmentRepository.findActiveById(attachment.id())).isEmpty();

        // 소프트삭제 후에는 같은 파일을 같은 강좌에 다시 붙일 수 있다 — 활성 행에만 유니크 제약이 걸린다.
        CourseAttachment reattached = new CourseAttachment(
            UUID.randomUUID(), courseId, file.id(), "무시되는_이름", 1, 0, createdAt);
        attachmentRepository.save(reattached);
        assertThat(attachmentRepository.findActiveByCourseId(courseId)).hasSize(1);
    }

    private Institution institution() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Institution(
            UUID.randomUUID(), uniqueCode("INST"), "첨부 테스트 기관", null, null,
            null, null, null, InstitutionStatus.ACTIVE, now, now, null
        );
    }

    private User user(UUID id) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new User(id, "강사", "attachment-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }

    private Course course(UUID id, UUID institutionId) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Course(id, institutionId, uniqueCode("COURSE"), "첨부 테스트 과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 1), null, null, 10, null, null, false, null,
            CourseStatus.DRAFT, now, now, null);
    }

    private StoredFile storedFile(UUID uploadedBy) {
        UUID fileId = UUID.randomUUID();
        return new StoredFile(fileId, StoredFilePurpose.COURSE_ATTACHMENT, "강의계획서.pdf",
            "application/pdf", 251_904L, uploadedBy + "/doc/" + fileId + ".pdf", uploadedBy,
            Instant.parse("2099-08-01T00:00:00Z"));
    }
}
