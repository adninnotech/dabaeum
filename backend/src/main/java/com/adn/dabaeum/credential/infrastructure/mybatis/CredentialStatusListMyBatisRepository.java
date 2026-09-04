package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CredentialStatusListMyBatisRepository implements CredentialStatusListRepository {

    private final CredentialStatusListMapper mapper;

    public CredentialStatusListMyBatisRepository(CredentialStatusListMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CredentialStatusList> findById(UUID listId) {
        return Optional.ofNullable(mapper.selectById(listId)).map(this::toDomain);
    }

    @Override
    public Optional<CredentialStatusList> findLatestByInstitutionId(UUID institutionId) {
        return Optional.ofNullable(mapper.selectLatestByInstitutionId(institutionId))
            .map(this::toDomain);
    }

    @Override
    public void insert(CredentialStatusList list) {
        mapper.insert(new CredentialStatusListRow(list.id(), list.institutionId(), list.listNo(),
            list.statusPurpose(), list.capacity(), list.createdAt()));
    }

    @Override
    public long countEntries(UUID listId) {
        return mapper.countEntries(listId);
    }

    /**
     * 이미 쓰인 칸이면 0건 갱신으로 끝나 false 가 된다.
     *
     * <p>유니크 제약 위반을 잡아서 false 로 바꾸지 않는다. PostgreSQL 은 문장 하나가 실패하면
     * 트랜잭션 전체를 중단시키므로, 삼켜서 호출자를 재시도시키면 이후 모든 질의가 25P02 로
     * 깨진다. 경쟁 트랜잭션이 같은 칸을 동시에 통과한 진짜 충돌만 예외로 올려보내고, 그때는
     * 발급 트랜잭션이 실패하고 Fabric 워커가 다시 시도한다.
     */
    @Override
    public boolean assignEntry(UUID credentialId, CredentialStatusListEntry entry) {
        return mapper.assignEntry(credentialId, entry.listId(), entry.index()) == 1;
    }

    @Override
    public Optional<CredentialStatusListEntry> findEntryByCredentialId(UUID credentialId) {
        CredentialStatusListEntryRow row = mapper.selectEntryByCredentialId(credentialId);
        if (row == null || row.statusListId() == null || row.statusListIndex() == null) {
            return Optional.empty();
        }
        return Optional.of(new CredentialStatusListEntry(row.statusListId(), row.statusListIndex()));
    }

    @Override
    public List<Integer> findRevokedIndexes(UUID listId) {
        return mapper.selectRevokedIndexes(listId);
    }

    private CredentialStatusList toDomain(CredentialStatusListRow row) {
        return new CredentialStatusList(row.id(), row.institutionId(), row.listNo(),
            row.statusPurpose(), row.capacity(), row.createdAt());
    }
}
