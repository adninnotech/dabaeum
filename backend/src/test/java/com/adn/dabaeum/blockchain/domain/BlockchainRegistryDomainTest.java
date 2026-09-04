package com.adn.dabaeum.blockchain.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BlockchainRegistryDomainTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-08-13T00:00:00Z");
    private static final Instant ISSUED_AT = Instant.parse("2026-08-12T01:02:03Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-08-13T04:05:06Z");
    private static final String DATA_KEY = "ABCD1234EFGH5678";
    private static final String VC_HASH = "a".repeat(64);

    @Test
    void provider_contract_exposes_only_supported_providers() {
        assertThat(BlockchainProvider.values())
            .containsExactly(BlockchainProvider.FABRIC_POC, BlockchainProvider.DAEGUCHAIN);
    }

    @Test
    void registry_status_maps_to_storage_contract_codes() {
        assertThat(RegistryStatus.ACTIVE.code()).isEqualTo("A");
        assertThat(RegistryStatus.REVOKED.code()).isEqualTo("R");
        assertThat(RegistryStatus.SUPERSEDED.code()).isEqualTo("S");
    }

    @Test
    void reference_accepts_exactly_one_identifier_strategy() {
        CredentialRegistryReference current = new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, DATA_KEY, null
        );
        CredentialRegistryReference legacy = new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, null, "CRD-2026-0001"
        );

        assertThat(current.chainKey()).isEqualTo("DCSTORE:" + DATA_KEY);
        assertThat(legacy.chainKey()).isEqualTo("CERT:CRD-2026-0001");

        assertThatThrownBy(() -> new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, DATA_KEY, "CRD-2026-0001"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reference_rejects_malformed_data_keys() {
        assertThatThrownBy(() -> new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, "abcd1234efgh5678", null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, "ABCD1234EFGH567", null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void daeguchain_reference_rejects_legacy_credential_numbers() {
        assertThatThrownBy(() -> new CredentialRegistryReference(
            BlockchainProvider.DAEGUCHAIN, null, "CRD-2026-0001"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void state_exposes_provider_neutral_registry_fields() {
        CredentialRegistryState state = new CredentialRegistryState(
            "DCSTORE:" + DATA_KEY,
            1,
            RegistryStatus.ACTIVE,
            VC_HASH,
            EVENT_TIME,
            BlockchainProvider.FABRIC_POC
        );

        assertThat(state.chainKey()).isEqualTo("DCSTORE:" + DATA_KEY);
        assertThat(state.schemaVersion()).isEqualTo(1);
        assertThat(state.status()).isEqualTo(RegistryStatus.ACTIVE);
        assertThat(state.vcHash()).isEqualTo(VC_HASH);
        assertThat(state.eventTime()).isEqualTo(EVENT_TIME);
        assertThat(state.provider()).isEqualTo(BlockchainProvider.FABRIC_POC);
    }

    @Test
    void receipt_exposes_provider_receipt_without_fabric_types() {
        BlockchainReceipt receipt = new BlockchainReceipt(
            BlockchainProvider.FABRIC_POC,
            "tx-001",
            null,
            42L,
            EVENT_TIME,
            true,
            "SUCCESS"
        );

        assertThat(receipt.provider()).isEqualTo(BlockchainProvider.FABRIC_POC);
        assertThat(receipt.transactionId()).isEqualTo("tx-001");
        assertThat(receipt.factHash()).isNull();
        assertThat(receipt.blockHeight()).isEqualTo(42L);
        assertThat(receipt.confirmedAt()).isEqualTo(EVENT_TIME);
        assertThat(receipt.confirmed()).isTrue();
        assertThat(receipt.resultCode()).isEqualTo("SUCCESS");
    }

    @Test
    void unconfirmed_receipt_rejects_a_confirmation_time() {
        assertThatThrownBy(() -> new BlockchainReceipt(
            BlockchainProvider.FABRIC_POC,
            "tx-001",
            null,
            null,
            EVENT_TIME,
            false,
            "PENDING"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(new BlockchainReceipt(
            BlockchainProvider.FABRIC_POC,
            "tx-002",
            null,
            null,
            null,
            true,
            "SUCCESS"
        ).confirmed()).isTrue();
    }

    @Test
    void commands_reject_malformed_vc_hashes() {
        CredentialRegistryReference reference = currentReference();

        assertThatThrownBy(() -> new CredentialRegistryCreate(
            reference, 1, RegistryStatus.ACTIVE, "A".repeat(64),
            "COMPACT_JWS_SHA256_V1", EVENT_TIME, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryUpdate(
            reference, 1, RegistryStatus.REVOKED, "a".repeat(63),
            "COMPACT_JWS_SHA256_V1", EVENT_TIME
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commands_reject_unknown_hash_versions() {
        CredentialRegistryReference current = currentReference();
        CredentialRegistryReference previous = legacyReference("CRD-2026-0001");
        CredentialRegistryReference replacement = legacyReference("CRD-2026-0002");

        assertThatThrownBy(() -> new CredentialRegistryCreate(
            current, 1, RegistryStatus.ACTIVE, VC_HASH, "SHA256", EVENT_TIME, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryUpdate(
            current, 1, RegistryStatus.REVOKED, VC_HASH, "SHA256", EVENT_TIME
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryReissue(
            previous, replacement, 1, VC_HASH, "SHA256", EVENT_TIME,
            legacyDetails(), REVOKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registry_models_reject_schema_versions_other_than_one() {
        CredentialRegistryReference current = currentReference();
        CredentialRegistryReference previous = legacyReference("CRD-2026-0001");
        CredentialRegistryReference replacement = legacyReference("CRD-2026-0002");

        assertThatThrownBy(() -> new CredentialRegistryState(
            current.chainKey(), 2, RegistryStatus.ACTIVE, VC_HASH, EVENT_TIME,
            BlockchainProvider.FABRIC_POC
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryCreate(
            current, 2, RegistryStatus.ACTIVE, VC_HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryUpdate(
            current, 2, RegistryStatus.REVOKED, VC_HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryReissue(
            previous, replacement, 2, VC_HASH, "ENVELOPE_SHA256_V0", EVENT_TIME,
            legacyDetails(), REVOKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_and_update_commands_enforce_their_allowed_statuses() {
        CredentialRegistryReference current = currentReference();

        assertThatThrownBy(() -> new CredentialRegistryCreate(
            current, 1, RegistryStatus.REVOKED, VC_HASH,
            "COMPACT_JWS_SHA256_V1", EVENT_TIME, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryCreate(
            current, 1, RegistryStatus.SUPERSEDED, VC_HASH,
            "COMPACT_JWS_SHA256_V1", EVENT_TIME, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryUpdate(
            current, 1, RegistryStatus.ACTIVE, VC_HASH,
            "COMPACT_JWS_SHA256_V1", EVENT_TIME
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void history_entry_requires_state_exactly_when_not_deleted() {
        CredentialRegistryState state = activeState();

        assertThatThrownBy(() -> new BlockchainHistoryEntry(
            "tx-001", state, EVENT_TIME, true
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockchainHistoryEntry(
            "tx-002", null, EVENT_TIME, false
        )).isInstanceOf(NullPointerException.class);

        assertThat(new BlockchainHistoryEntry("tx-003", null, EVENT_TIME, true).deleted())
            .isTrue();
        assertThat(new BlockchainHistoryEntry("tx-004", state, EVENT_TIME, false).state())
            .isEqualTo(state);
    }

    @Test
    void history_query_enforces_daeguchain_paging_bounds() {
        assertThat(new BlockchainHistoryQuery(10, 0, false).limit()).isEqualTo(10);
        assertThat(new BlockchainHistoryQuery(50, 3, true).reverse()).isTrue();

        assertThatThrownBy(() -> new BlockchainHistoryQuery(9, 0, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockchainHistoryQuery(51, 0, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockchainHistoryQuery(10, -1, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reissue_is_restricted_to_distinct_legacy_references_from_one_provider() {
        CredentialRegistryReference previous = new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, null, "CRD-2026-0001"
        );
        CredentialRegistryReference replacement = new CredentialRegistryReference(
            BlockchainProvider.FABRIC_POC, null, "CRD-2026-0002"
        );

        CredentialRegistryReissue command = new CredentialRegistryReissue(
            previous, replacement, 1, VC_HASH, "ENVELOPE_SHA256_V0", EVENT_TIME,
            legacyDetails(), REVOKED_AT
        );

        assertThat(command.previousReference()).isEqualTo(previous);
        assertThat(command.replacementReference()).isEqualTo(replacement);
        assertThatThrownBy(() -> new CredentialRegistryReissue(
            currentReference(), replacement, 1, VC_HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME,
            legacyDetails(), REVOKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryReissue(
            previous, previous, 1, VC_HASH, "ENVELOPE_SHA256_V0", EVENT_TIME,
            legacyDetails(), REVOKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacy_create_requires_details_and_current_create_rejects_them() {
        CredentialRegistryReference current = currentReference();
        CredentialRegistryReference legacy = legacyReference("CRD-2026-0001");

        assertThatThrownBy(() -> new CredentialRegistryCreate(
            legacy, 1, RegistryStatus.ACTIVE, VC_HASH, "ENVELOPE_SHA256_V0", EVENT_TIME,
            null
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CredentialRegistryCreate(
            current, 1, RegistryStatus.ACTIVE, VC_HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME,
            legacyDetails()
        )).isInstanceOf(IllegalArgumentException.class);

        CredentialRegistryCreate command = new CredentialRegistryCreate(
            legacy, 1, RegistryStatus.ACTIVE, VC_HASH, "ENVELOPE_SHA256_V0", EVENT_TIME,
            legacyDetails()
        );
        assertThat(command.legacyDetails().issuedAt()).isEqualTo(ISSUED_AT);
    }

    @Test
    void legacy_issue_details_and_reissue_require_complete_explicit_values() {
        assertThatThrownBy(() -> new LegacyCredentialIssueDetails(
            "subject", "sha256:" + "2".repeat(64),
            "LIFELONG_EDUCATION_COMPLETION", ISSUED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyCredentialIssueDetails(
            "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64), "", ISSUED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyCredentialIssueDetails(
            "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64),
            "GRADUATION", ISSUED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        CredentialRegistryReference previous = legacyReference("CRD-2026-0001");
        CredentialRegistryReference replacement = legacyReference("CRD-2026-0002");
        assertThatThrownBy(() -> new CredentialRegistryReissue(
            previous, replacement, 1, VC_HASH, "ENVELOPE_SHA256_V0", EVENT_TIME,
            null, REVOKED_AT
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CredentialRegistryReissue(
            previous, replacement, 1, VC_HASH, "ENVELOPE_SHA256_V0", EVENT_TIME,
            legacyDetails(), null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void commands_reject_hash_versions_that_do_not_match_reference_strategy() {
        assertThatThrownBy(() -> new CredentialRegistryCreate(
            currentReference(), 1, RegistryStatus.ACTIVE, VC_HASH,
            "ENVELOPE_SHA256_V0", EVENT_TIME, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryCreate(
            legacyReference("CRD-2026-0001"), 1, RegistryStatus.ACTIVE, VC_HASH,
            "COMPACT_JWS_SHA256_V1", EVENT_TIME, legacyDetails()
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryUpdate(
            legacyReference("CRD-2026-0001"), 1, RegistryStatus.REVOKED, VC_HASH,
            "COMPACT_JWS_SHA256_V1", EVENT_TIME
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialRegistryReissue(
            legacyReference("CRD-2026-0001"), legacyReference("CRD-2026-0002"),
            1, VC_HASH, "COMPACT_JWS_SHA256_V1", EVENT_TIME, legacyDetails(), REVOKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static CredentialRegistryReference currentReference() {
        return new CredentialRegistryReference(BlockchainProvider.FABRIC_POC, DATA_KEY, null);
    }

    private static CredentialRegistryReference legacyReference(String credentialNo) {
        return new CredentialRegistryReference(BlockchainProvider.FABRIC_POC, null, credentialNo);
    }

    private static LegacyCredentialIssueDetails legacyDetails() {
        return new LegacyCredentialIssueDetails(
            "sha256:" + "1".repeat(64),
            "sha256:" + "2".repeat(64),
            "LIFELONG_EDUCATION_COMPLETION",
            ISSUED_AT
        );
    }

    private static CredentialRegistryState activeState() {
        return new CredentialRegistryState(
            "DCSTORE:" + DATA_KEY,
            1,
            RegistryStatus.ACTIVE,
            VC_HASH,
            EVENT_TIME,
            BlockchainProvider.FABRIC_POC
        );
    }
}
