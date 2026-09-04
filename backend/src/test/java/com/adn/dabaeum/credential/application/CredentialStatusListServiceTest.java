package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class CredentialStatusListServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T01:02:03Z");
    private static final UUID LIST_ID = UUID.fromString("70000000-0000-0000-0000-000000000007");
    private static final UUID INSTITUTION_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String BASE = "https://vc.example.test/api/v1/vc";

    private final CredentialUriProvider uriProvider = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));
    private final BitstringStatusListEncoder encoder = new BitstringStatusListEncoder();
    private final RecordingProof proof = new RecordingProof();
    private final FakeRepository repository = new FakeRepository();

    @Test
    void buildsASignedStatusListWhoseOnlyContentIsTheRevocationBits() {
        repository.list = list();
        repository.revoked = List.of(94_567, 12);

        SignedCredentialEnvelope envelope = service(proof).document(LIST_ID);

        assertThat(envelope).isSameAs(proof.signed);
        String json = proof.document.canonicalJson();
        String listUrl = BASE + "/status-lists/" + LIST_ID;
        assertThat(json).startsWith(
            "{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],"
                + "\"id\":\"" + listUrl + "\","
                + "\"type\":[\"VerifiableCredential\",\"BitstringStatusListCredential\"],"
                + "\"issuer\":\"" + BASE + "/issuers/" + INSTITUTION_ID + "\","
                + "\"validFrom\":\"2026-09-03T01:02:03Z\","
                + "\"credentialSubject\":{\"id\":\"" + listUrl + "#list\","
                + "\"type\":\"BitstringStatusList\",\"statusPurpose\":\"revocation\","
                + "\"encodedList\":\"u");
        String encoded = json.substring(json.indexOf("\"encodedList\":\"") + 15, json.length() - 3);
        assertThat(encoder.decode(encoded, CredentialStatusList.MINIMUM_CAPACITY).stream().boxed())
            .containsExactly(12, 94_567);
        assertThat(json).doesNotContain("credentialNo", "completionId", "attendanceRate", "user");
    }

    @Test
    void unknownListIs404AndMissingSigningServiceIs503() {
        assertThatThrownBy(() -> service(proof).document(LIST_ID))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));

        repository.list = list();
        assertThatThrownBy(() -> service(null).document(LIST_ID))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(proof.document).isNull();
    }

    private CredentialStatusListService service(CredentialStatusListProofService proofService) {
        return new CredentialStatusListService(repository,
            new CredentialStatusListDocumentFactory(new ObjectMapper(),
                new DabaeumUrnCredentialIdentifierProvider(uriProvider)),
            encoder, proofService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CredentialStatusList list() {
        return new CredentialStatusList(LIST_ID, INSTITUTION_ID, 1,
            CredentialStatusList.REVOCATION, CredentialStatusList.MINIMUM_CAPACITY, NOW);
    }

    private static final class RecordingProof implements CredentialStatusListProofService {
        private CredentialDocument document;
        private final SignedCredentialEnvelope signed =
            new SignedCredentialEnvelope("application/vc+jwt", "header.payload.signature");

        @Override
        public SignedCredentialEnvelope signStatusList(CredentialDocument document) {
            this.document = document;
            return signed;
        }

        @Override
        public CredentialDocument verifyStatusList(SignedCredentialEnvelope envelope) {
            return document;
        }
    }

    private static final class FakeRepository implements CredentialStatusListRepository {
        private CredentialStatusList list;
        private List<Integer> revoked = List.of();

        @Override
        public Optional<CredentialStatusList> findById(UUID listId) {
            return Optional.ofNullable(list).filter(value -> value.id().equals(listId));
        }

        @Override
        public Optional<CredentialStatusList> findLatestByInstitutionId(UUID institutionId) {
            return Optional.ofNullable(list);
        }

        @Override
        public void insert(CredentialStatusList list) {
            this.list = list;
        }

        @Override
        public long countEntries(UUID listId) {
            return 0;
        }

        @Override
        public boolean assignEntry(UUID credentialId, CredentialStatusListEntry entry) {
            return true;
        }

        @Override
        public Optional<CredentialStatusListEntry> findEntryByCredentialId(UUID credentialId) {
            return Optional.empty();
        }

        @Override
        public List<Integer> findRevokedIndexes(UUID listId) {
            return revoked;
        }
    }
}
