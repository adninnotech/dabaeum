package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;

class CredentialDocumentFactoryTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID COMPLETION_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID ENROLLMENT_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID COURSE_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");
    private static final Instant VALID_FROM = Instant.parse("2026-08-05T01:02:03Z");
    private static final Instant VALID_UNTIL = Instant.parse("2027-08-05T01:02:03Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-04T10:20:30Z");
    private static final UUID LIST_ID = UUID.fromString("70000000-0000-0000-0000-000000000007");
    private static final CredentialStatusListEntry STATUS_ENTRY =
        new CredentialStatusListEntry(LIST_ID, 94_567);

    private final CredentialUriProvider uriProvider = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));
    private final CredentialDocumentFactory factory = new CredentialDocumentFactory(
        new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(uriProvider));

    @Test
    void createsExactCanonicalWhitelistedCredentialDocument() {
        var document = factory.create(CREDENTIAL_ID, STATUS_ENTRY, INSTITUTION_ID, USER_ID, COMPLETION_ID,
            ENROLLMENT_ID, COURSE_ID, VALID_FROM, VALID_UNTIL, COMPLETED_AT,
            new BigDecimal("90.00"), 1200, new BigDecimal("3.00"));

        assertThat(document.canonicalJson()).isEqualTo(
            "{\"@context\":[\"https://www.w3.org/ns/credentials/v2\","
                + "\"https://vc.example.test/api/v1/vc/contexts/lifelong-education/v1\"],"
                + "\"id\":\"urn:uuid:10000000-0000-0000-0000-000000000001\","
                + "\"type\":[\"VerifiableCredential\",\"LifelongEducationCompletionCredential\"],"
                + "\"issuer\":\"https://vc.example.test/api/v1/vc/issuers/"
                + "20000000-0000-0000-0000-000000000002\","
                + "\"validFrom\":\"2026-08-05T01:02:03Z\","
                + "\"validUntil\":\"2027-08-05T01:02:03Z\","
                + "\"credentialSubject\":{"
                + "\"id\":\"urn:dabaeum:user:30000000-0000-0000-0000-000000000003\","
                + "\"completionId\":\"40000000-0000-0000-0000-000000000004\","
                + "\"enrollmentId\":\"50000000-0000-0000-0000-000000000005\","
                + "\"courseId\":\"60000000-0000-0000-0000-000000000006\","
                + "\"completedAt\":\"2026-08-04T10:20:30Z\","
                + "\"attendanceRate\":90.00,\"completedMinutes\":1200,\"creditValue\":3.00},"
                + "\"credentialStatus\":{"
                + "\"id\":\"https://vc.example.test/api/v1/vc/status-lists/"
                + "70000000-0000-0000-0000-000000000007#94567\","
                + "\"type\":\"BitstringStatusListEntry\","
                + "\"statusPurpose\":\"revocation\","
                + "\"statusListIndex\":\"94567\","
                + "\"statusListCredential\":\"https://vc.example.test/api/v1/vc/status-lists/"
                + "70000000-0000-0000-0000-000000000007\"}}"
        );
    }

    @Test
    void omitsOptionalValuesAndRejectsInvalidValidityWindow() {
        var document = factory.create(CREDENTIAL_ID, STATUS_ENTRY, INSTITUTION_ID, USER_ID, COMPLETION_ID,
            ENROLLMENT_ID, COURSE_ID, VALID_FROM, null, COMPLETED_AT,
            new BigDecimal("90.00"), 1200, null);

        assertThat(document.canonicalJson()).doesNotContain("validUntil", "creditValue");
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(
            CREDENTIAL_ID, STATUS_ENTRY, INSTITUTION_ID, USER_ID, COMPLETION_ID, ENROLLMENT_ID, COURSE_ID,
            VALID_FROM, VALID_FROM, COMPLETED_AT, new BigDecimal("90.00"), 1200, null));
    }
}
