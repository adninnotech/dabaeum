package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialCourseView;
import com.adn.dabaeum.credential.domain.CredentialQueryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CredentialQueryMyBatisRepository implements CredentialQueryRepository {

    private final CredentialQueryMapper mapper;

    public CredentialQueryMyBatisRepository(CredentialQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CredentialCourseView> findCourseByCredentialId(UUID credentialId) {
        if (credentialId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectCourseByCredentialId(credentialId))
            .map(this::toDomain);
    }

    @Override
    public List<CredentialCourseView> findCoursesByCredentialIds(List<UUID> credentialIds) {
        if (credentialIds == null || credentialIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectCoursesByCredentialIds(credentialIds).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Credential> findByUserIdAndInstitutionIds(
        UUID userId, List<UUID> institutionIds, int limit, int offset, String sort
    ) {
        if (institutionIds == null || institutionIds.isEmpty()) {
            return List.of();
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        CredentialMyBatisRepository.CredentialSort credentialSort =
            CredentialMyBatisRepository.CredentialSort.from(sort);
        return mapper.selectByUserIdAndInstitutionIds(
                userId, institutionIds, limit, offset, credentialSort.field, credentialSort.direction)
            .stream()
            .map(CredentialMyBatisRepository::toDomain)
            .toList();
    }

    @Override
    public long countByUserIdAndInstitutionIds(UUID userId, List<UUID> institutionIds) {
        if (institutionIds == null || institutionIds.isEmpty()) {
            return 0L;
        }
        return mapper.countByUserIdAndInstitutionIds(userId, institutionIds);
    }

    private CredentialCourseView toDomain(CredentialCourseViewRow row) {
        return new CredentialCourseView(
            row.credentialId(), row.courseId(), row.courseTitle(),
            row.courseCode(), row.institutionName());
    }
}
