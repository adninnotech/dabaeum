package com.adn.dabaeum.credential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.infrastructure.mybatis.CredentialRow;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CredentialMapperContractTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V13__add_blockchain_registry_compatibility.sql");
    private static final Path CREDENTIAL_MAPPER = Path.of(
        "src/main/resources/mybatis/mapper/credential/CredentialMapper.xml");
    private static final Path BLOCKCHAIN_TRANSACTION_MAPPER = Path.of(
        "src/main/resources/mybatis/mapper/fabric/BlockchainTransactionMapper.xml");
    private static final Path PENDING_REQUEST_MAPPER = Path.of(
        "src/main/resources/mybatis/mapper/fabric/PendingBlockchainRequestMapper.xml");
    private static final Path PENDING_REQUEST_ADAPTER = Path.of(
        "src/main/java/com/adn/dabaeum/fabric/infrastructure/mybatis/"
            + "PendingBlockchainRequestMyBatisAdapter.java");

    private static final List<String> EXPECTED_ROW_COMPONENTS = List.of(
        "id", "credentialGroupId", "previousCredentialId", "credentialNo", "versionNo",
        "issuerIdentifier", "subjectIdentifier", "credentialType", "status", "validFrom",
        "validUntil", "vcPayload", "vcHash", "chainKey", "vcHashVersion", "issuedAt",
        "revokedAt", "revocationReason", "failureCode", "failureMessage", "createdAt",
        "updatedAt");

    private static final List<String> EXPECTED_MAPPED_COLUMNS = List.of(
        "id", "credential_group_id", "previous_credential_id", "credential_no", "version_no",
        "issuer_identifier", "subject_identifier", "credential_type", "status", "valid_from",
        "valid_until", "vc_payload", "vc_hash", "chain_key", "vc_hash_version", "issued_at",
        "revoked_at", "revocation_reason", "failure_code", "failure_message", "created_at",
        "updated_at");

    @Test
    void migrationAddsRegistryColumnsConstraintsBackfillAndPartialUniqueness() throws Exception {
        assertThat(MIGRATION).exists();

        String sql = normalize(Files.readString(MIGRATION));
        assertThat(sql)
            .contains("ALTER TABLE tb_credentials ADD COLUMN chain_key VARCHAR(20)")
            .contains("ADD COLUMN vc_hash_version VARCHAR(32)")
            .contains("chain_key IS NULL OR chain_key ~ '^[A-Z0-9]{16}$'")
            .contains("vc_hash_version IS NULL OR vc_hash_version IN (")
            .contains("'ENVELOPE_SHA256_V0', 'COMPACT_JWS_SHA256_V1'")
            .contains("ADD CONSTRAINT ck_tb_credentials_registry_metadata CHECK")
            .contains("chain_key IS NULL")
            .contains("vc_hash_version = 'ENVELOPE_SHA256_V0'")
            .contains("chain_key IS NOT NULL")
            .contains("vc_hash_version = 'COMPACT_JWS_SHA256_V1'")
            .contains("UPDATE tb_credentials SET vc_hash_version = 'ENVELOPE_SHA256_V0' "
                + "WHERE vc_hash IS NOT NULL AND vc_hash_version IS NULL")
            .contains("CREATE UNIQUE INDEX uq_tb_credentials_chain_key")
            .contains("ON tb_credentials (chain_key) WHERE chain_key IS NOT NULL")
            .doesNotContain("DROP TABLE", "TRUNCATE", "UPDATE tb_blockchain_transactions");
    }

    @Test
    void credentialDomainRowAndMapperCarryRegistryFieldsInTheSameOrder() throws Exception {
        assertThat(recordComponents(Credential.class)).contains("chainKey", "vcHashVersion");
        assertThat(recordComponents(CredentialRow.class)).containsExactlyElementsOf(
            EXPECTED_ROW_COMPONENTS);

        String xml = Files.readString(CREDENTIAL_MAPPER);
        assertThat(constructorColumns(xml)).containsExactlyElementsOf(EXPECTED_MAPPED_COLUMNS);

        String reusableColumns = normalize(element(xml, "sql", "credentialColumns"));
        String insert = normalize(element(xml, "insert", "insert"));
        String conditionalInsert = normalize(element(
            xml, "insert", "insertIfChainKeyAvailable"));
        String userSelect = normalize(element(xml, "select", "selectByUserId"));
        String update = normalize(element(xml, "update", "update"));

        assertThat(reusableColumns).contains("vc_payload, vc_hash, chain_key, vc_hash_version");
        assertThat(insert)
            .contains("vc_payload, vc_hash, chain_key, vc_hash_version")
            .contains("#{vcPayload, jdbcType=VARCHAR}, #{vcHash, jdbcType=VARCHAR}, "
                + "#{chainKey, jdbcType=VARCHAR}, #{vcHashVersion, jdbcType=VARCHAR}");
        assertThat(conditionalInsert)
            .contains("vc_payload, vc_hash, chain_key, vc_hash_version")
            .contains("ON CONFLICT (chain_key) WHERE chain_key IS NOT NULL DO NOTHING");
        assertThat(userSelect)
            .contains("c.vc_payload, c.vc_hash, c.chain_key, c.vc_hash_version");
        assertThat(update)
            .contains("chain_key = #{chainKey, jdbcType=VARCHAR}")
            .contains("vc_hash_version = #{vcHashVersion, jdbcType=VARCHAR}");
    }

    @Test
    void providerAwareWorkerQueriesSeparateIdempotencyAndClaimOnlyAllowedNetworks() throws Exception {
        String transactionMapper = normalize(Files.readString(BLOCKCHAIN_TRANSACTION_MAPPER));
        String pendingMapper = normalize(Files.readString(PENDING_REQUEST_MAPPER));
        String pendingAdapter = Files.readString(PENDING_REQUEST_ADAPTER);

        assertThat(normalize(element(transactionMapper, "select", "claimDue")))
            .contains("collection=\"networks\"")
            .doesNotContain("network = 'DABAEUM_FABRIC'");
        assertThat(normalize(element(transactionMapper, "select", "claimStale")))
            .contains("collection=\"networks\"")
            .doesNotContain("network = 'DABAEUM_FABRIC'");
        assertThat(normalize(element(
            pendingMapper, "select", "selectByNetworkAndIdempotencyKey")))
            .contains("network = #{network}");
        assertThat(normalize(element(pendingMapper, "select", "existsPendingOperationForGroup")))
            .contains("transaction.network IN ('DABAEUM_FABRIC', 'FABRIC_POC', 'DAEGUCHAIN')");
        assertThat(pendingAdapter)
            .contains("request.network(), request.idempotencyKey()")
            .doesNotContain("private static final String NETWORK");
    }

    private static List<String> recordComponents(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    }

    private static List<String> constructorColumns(String xml) {
        String constructor = element(xml, "constructor", null);
        Matcher matcher = Pattern.compile("column=\"([^\"]+)\"").matcher(constructor);
        return matcher.results().map(result -> result.group(1)).toList();
    }

    private static String element(String xml, String tag, String id) {
        String idPattern = id == null ? "" : "[^>]*id=\"" + Pattern.quote(id) + "\"";
        Matcher matcher = Pattern.compile(
            "<" + tag + idPattern + "[^>]*>(.*?)</" + tag + ">", Pattern.DOTALL)
            .matcher(xml);
        assertThat(matcher.find()).as("%s element %s", tag, id).isTrue();
        return matcher.group(1);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
