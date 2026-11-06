package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.LegacySignKeyBackfillRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class LegacySignKeyBackfillMyBatisRepository implements LegacySignKeyBackfillRepository {

    private final LegacySignKeyBackfillMapper mapper;

    public LegacySignKeyBackfillMyBatisRepository(LegacySignKeyBackfillMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<UUID> findInstitutionsWithUnlinkedRows() {
        return mapper.selectInstitutionsWithUnlinkedRows().stream()
            .map(UUID::fromString)
            .toList();
    }

    @Override
    public int linkCredentials(UUID institutionId, UUID signKeyId) {
        return mapper.linkCredentials(institutionId, signKeyId);
    }

    @Override
    public int linkStatusLists(UUID institutionId, UUID signKeyId) {
        return mapper.linkStatusLists(institutionId, signKeyId);
    }
}
