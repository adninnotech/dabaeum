package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class CredentialStatusListAllocatorTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private final FakeRepository repository = new FakeRepository();

    @Test
    void createsTheFirstListOnDemandAndAssignsARandomUnusedIndex() {
        CredentialStatusListAllocator allocator = allocator(new Random(7));
        UUID credentialId = UUID.randomUUID();

        CredentialStatusListEntry entry = allocator.allocate(credentialId, INSTITUTION);

        CredentialStatusList list = repository.lists.get(entry.listId());
        assertThat(list.institutionId()).isEqualTo(INSTITUTION);
        assertThat(list.listNo()).isEqualTo(1);
        assertThat(list.capacity()).isEqualTo(CredentialStatusList.MINIMUM_CAPACITY);
        assertThat(entry.index()).isBetween(0, CredentialStatusList.MINIMUM_CAPACITY - 1);
        assertThat(repository.findEntryByCredentialId(credentialId)).contains(entry);
    }

    @Test
    void reusesTheEntryAlreadyAssignedToTheCredential() {
        CredentialStatusListAllocator allocator = allocator(new Random(7));
        UUID credentialId = UUID.randomUUID();

        CredentialStatusListEntry first = allocator.allocate(credentialId, INSTITUTION);
        CredentialStatusListEntry second = allocator.allocate(credentialId, INSTITUTION);

        assertThat(second).isEqualTo(first);
        assertThat(repository.lists).hasSize(1);
    }

    @Test
    void indexesAreNotSequentialAndNeverCollide() {
        CredentialStatusListAllocator allocator = allocator(new Random(11));
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            indexes.add(allocator.allocate(UUID.randomUUID(), INSTITUTION).index());
        }

        assertThat(new HashSet<>(indexes)).hasSize(50);
        // 발급 순서대로 채우면 인덱스가 발급 시점과 규모를 드러낸다. 오름차순이 아니어야 한다.
        List<Integer> ascending = new ArrayList<>(indexes);
        ascending.sort(null);
        assertThat(indexes).isNotEqualTo(ascending);
    }

    @Test
    void retriesWhenTheRandomIndexIsAlreadyTaken() {
        UUID takenCredential = UUID.randomUUID();
        allocator(fixed(5, 5, 5, 9)).allocate(takenCredential, INSTITUTION);
        assertThat(repository.findEntryByCredentialId(takenCredential).orElseThrow().index())
            .isEqualTo(5);

        CredentialStatusListEntry entry = allocator(fixed(5, 5, 5, 9))
            .allocate(UUID.randomUUID(), INSTITUTION);

        assertThat(entry.index()).isEqualTo(9);
    }

    @Test
    void opensTheNextListWhenTheCurrentOneIsFull() {
        CredentialStatusListAllocator allocator = allocator(new Random(3));
        UUID firstList = allocator.allocate(UUID.randomUUID(), INSTITUTION).listId();
        repository.fullLists.add(firstList);

        CredentialStatusListEntry entry = allocator.allocate(UUID.randomUUID(), INSTITUTION);

        assertThat(entry.listId()).isNotEqualTo(firstList);
        assertThat(repository.lists.get(entry.listId()).listNo()).isEqualTo(2);
    }

    @Test
    void failsInsteadOfLoopingForeverWhenNoIndexCanBeTaken() {
        allocator(fixed(1)).allocate(UUID.randomUUID(), INSTITUTION);

        assertThatIllegalStateException()
            .isThrownBy(() -> allocator(fixed(1)).allocate(UUID.randomUUID(), INSTITUTION));
    }

    private CredentialStatusListAllocator allocator(RandomGenerator random) {
        return new CredentialStatusListAllocator(
            repository, Clock.fixed(NOW, ZoneOffset.UTC), random);
    }

    private static RandomGenerator fixed(int... values) {
        return new RandomGenerator() {
            private int position;

            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int nextInt(int bound) {
                int value = values[Math.min(position, values.length - 1)];
                position++;
                return value;
            }
        };
    }

    private static final class FakeRepository implements CredentialStatusListRepository {
        private final Map<UUID, CredentialStatusList> lists = new LinkedHashMap<>();
        private final Map<UUID, CredentialStatusListEntry> entries = new HashMap<>();
        private final Set<UUID> fullLists = new HashSet<>();

        @Override
        public Optional<CredentialStatusList> findById(UUID listId) {
            return Optional.ofNullable(lists.get(listId));
        }

        @Override
        public Optional<CredentialStatusList> findLatestByInstitutionId(UUID institutionId) {
            return lists.values().stream()
                .filter(list -> list.institutionId().equals(institutionId))
                .max((a, b) -> Integer.compare(a.listNo(), b.listNo()));
        }

        @Override
        public void insert(CredentialStatusList list) {
            lists.put(list.id(), list);
        }

        @Override
        public long countEntries(UUID listId) {
            if (fullLists.contains(listId)) {
                return CredentialStatusList.MINIMUM_CAPACITY;
            }
            return entries.values().stream().filter(entry -> entry.listId().equals(listId)).count();
        }

        @Override
        public boolean assignEntry(UUID credentialId, CredentialStatusListEntry entry) {
            if (entries.containsKey(credentialId) || entries.containsValue(entry)) {
                return false;
            }
            entries.put(credentialId, entry);
            return true;
        }

        @Override
        public Optional<CredentialStatusListEntry> findEntryByCredentialId(UUID credentialId) {
            return Optional.ofNullable(entries.get(credentialId));
        }

        @Override
        public List<Integer> findRevokedIndexes(UUID listId) {
            return List.of();
        }
    }
}
