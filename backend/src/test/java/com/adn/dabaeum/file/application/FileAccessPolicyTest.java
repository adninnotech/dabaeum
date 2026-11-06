package com.adn.dabaeum.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.FileAccessRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileAccessPolicyTest {

    @Mock FileAccessRepository mapper;
    private final UUID fileId = UUID.randomUUID();
    private final UUID uploader = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void unlinkedFileIsUploaderOnly() {
        FileAccessPolicy policy = new FileAccessPolicy(mapper, clock);
        StoredFile file = file(StoredFilePurpose.PROFILE);
        assertThat(policy.canRead(file, null)).isFalse();
        assertThat(policy.canRead(file, actor(uploader, "LEARNER", null))).isTrue();
        assertThat(policy.canRead(file, actor(UUID.randomUUID(), "PLATFORM_ADMIN", null)))
            .isFalse();
    }

    @Test
    void publicNoticeRequiresPublishedLink() {
        FileAccessPolicy policy = new FileAccessPolicy(mapper, clock);
        StoredFile file = file(StoredFilePurpose.NOTICE_ATTACHMENT);
        when(mapper.publiclyLinked(fileId, "NOTICE_ATTACHMENT", clock.instant())).thenReturn(true);
        assertThat(policy.canRead(file, null)).isTrue();
    }

    @Test
    void courseAttachmentRequiresLoginWhenCourseIsPublic() {
        FileAccessPolicy policy = new FileAccessPolicy(mapper, clock);
        StoredFile file = file(StoredFilePurpose.COURSE_ATTACHMENT);
        when(mapper.publiclyLinked(fileId, "COURSE_ATTACHMENT", clock.instant()))
            .thenReturn(true);
        when(mapper.linked(fileId, "COURSE_ATTACHMENT")).thenReturn(true);
        assertThat(policy.canRead(file, null)).isFalse();
        assertThat(policy.canRead(file, actor(UUID.randomUUID(), "LEARNER", null)))
            .isTrue();
    }

    private StoredFile file(StoredFilePurpose purpose) {
        return new StoredFile(fileId, purpose, "file.png", "image/png", 1,
            "file.png", uploader, clock.instant());
    }

    private AuthenticatedUserContext actor(UUID id, String role, UUID institutionId) {
        return new AuthenticatedUserContext(id, "LOCAL",
            Set.of(new AuthenticatedRole(role, institutionId)));
    }
}
