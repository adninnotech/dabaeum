package com.adn.dabaeum.file.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.FileAccessRepository;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "dev"})
public class FileAccessPolicy {

    private final FileAccessRepository mapper;
    private final Clock clock;

    public FileAccessPolicy(FileAccessRepository mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public boolean publiclyReadable(StoredFile file) {
        return file.purpose() != StoredFilePurpose.COURSE_ATTACHMENT
            && file.purpose() != StoredFilePurpose.INQUIRY
            && mapper.publiclyLinked(file.id(), file.purpose().name(), clock.instant());
    }

    public boolean canRead(StoredFile file, AuthenticatedUserContext actor) {
        if (publiclyReadable(file)) return true;
        boolean linked = file.purpose() != StoredFilePurpose.INQUIRY
            && mapper.linked(file.id(), file.purpose().name());
        if (actor == null) return false;
        if (file.purpose() == StoredFilePurpose.COURSE_ATTACHMENT && linked) {
            if (mapper.publiclyLinked(file.id(), file.purpose().name(), clock.instant())) return true;
            return isManager(file, actor);
        }
        if (file.uploadedBy().equals(actor.userId())) return true;
        return linked && isManager(file, actor);
    }

    private boolean isManager(StoredFile file, AuthenticatedUserContext actor) {
        if (actor.roles().stream().anyMatch(role -> role.role().equals("PLATFORM_ADMIN"))) {
            return true;
        }
        return actor.roles().stream()
            .filter(role -> role.institutionId() != null
                && (role.role().equals("INSTITUTION_ADMIN") || role.role().equals("INSTRUCTOR")))
            .anyMatch(role -> mapper.managedByInstitution(
                file.id(), file.purpose().name(), role.institutionId()));
    }
}
