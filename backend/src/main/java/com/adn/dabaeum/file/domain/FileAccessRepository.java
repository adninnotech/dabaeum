package com.adn.dabaeum.file.domain;

import java.time.Instant;
import java.util.UUID;

public interface FileAccessRepository {

    boolean publiclyLinked(UUID fileId, String purpose, Instant now);

    boolean linked(UUID fileId, String purpose);

    boolean managedByInstitution(UUID fileId, String purpose, UUID institutionId);
}
