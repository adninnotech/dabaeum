package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 발급 시점에 Credential 에 상태 리스트 칸을 배정한다.
 *
 * <p>칸은 무작위로 고른다. 순서대로 채우면 인덱스가 발급 순서와 규모를 드러내기 때문이다.
 * 한 번 배정한 칸은 발급 실패로 버려지더라도 재사용하지 않는다. 리스트가 차면 다음 번호의
 * 리스트를 만든다.
 */
@Component
public class CredentialStatusListAllocator {

    static final int MAX_ATTEMPTS = 64;

    private final CredentialStatusListRepository repository;
    private final Clock clock;
    private final RandomGenerator random;

    @Autowired
    public CredentialStatusListAllocator(CredentialStatusListRepository repository, Clock clock) {
        this(repository, clock, new SecureRandom());
    }

    CredentialStatusListAllocator(
        CredentialStatusListRepository repository,
        Clock clock,
        RandomGenerator random
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public CredentialStatusListEntry allocate(UUID credentialId, UUID institutionId) {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(institutionId, "institutionId");
        Optional<CredentialStatusListEntry> existing = repository.findEntryByCredentialId(credentialId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CredentialStatusList list = openList(institutionId);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            CredentialStatusListEntry candidate = new CredentialStatusListEntry(
                list.id(), random.nextInt(list.capacity()));
            if (repository.assignEntry(credentialId, candidate)) {
                return candidate;
            }
            // 다른 처리 흐름이 먼저 배정했으면 그 자리를 그대로 쓴다. 아니면 칸 충돌이므로 다시 고른다.
            Optional<CredentialStatusListEntry> assigned = repository.findEntryByCredentialId(credentialId);
            if (assigned.isPresent()) {
                return assigned.get();
            }
        }
        throw new IllegalStateException("Credential status list index allocation failed");
    }

    private CredentialStatusList openList(UUID institutionId) {
        Optional<CredentialStatusList> latest = repository.findLatestByInstitutionId(institutionId);
        if (latest.isPresent() && repository.countEntries(latest.get().id()) < latest.get().capacity()) {
            return latest.get();
        }
        int nextListNo = latest.map(list -> list.listNo() + 1).orElse(1);
        CredentialStatusList created = new CredentialStatusList(UUID.randomUUID(), institutionId,
            nextListNo, CredentialStatusList.REVOCATION, CredentialStatusList.MINIMUM_CAPACITY,
            clock.instant());
        // 같은 번호를 동시에 만들면 유니크 제약에서 실패한다. 여기서 잡아 회복하려 해도
        // PostgreSQL 은 이미 트랜잭션을 중단시킨 상태이므로, 발급을 실패시키고 워커가 다시
        // 시도하게 두는 것이 맞다.
        repository.insert(created);
        return created;
    }
}
