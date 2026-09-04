package com.adn.dabaeum.credential.infrastructure.crypto;

import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.SerializationFeature;

public class Ed25519JoseCredentialProofService
    implements CredentialProofService, CredentialStatusListProofService {

    private static final String MEDIA_TYPE = "application/vc+jwt";
    private static final String W3C_CONTEXT = "https://www.w3.org/ns/credentials/v2";
    private static final Pattern BASE64URL = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern UUID_TEXT = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern CREDENTIAL_NUMBER =
        Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");
    private static final List<String> LEGACY_ROOT_FIELDS = List.of(
        "@context", "id", "type", "issuer", "validFrom", "credentialSubject");
    private static final List<String> LEGACY_ROOT_FIELDS_WITH_VALID_UNTIL = List.of(
        "@context", "id", "type", "issuer", "validFrom", "validUntil", "credentialSubject");
    private static final List<String> W3C_ROOT_FIELDS = List.of(
        "@context", "id", "type", "issuer", "validFrom", "credentialSubject",
        "credentialStatus");
    private static final List<String> W3C_ROOT_FIELDS_WITH_VALID_UNTIL = List.of(
        "@context", "id", "type", "issuer", "validFrom", "validUntil",
        "credentialSubject", "credentialStatus");
    private static final List<String> SUBJECT_FIELDS = List.of(
        "id", "completionId", "enrollmentId", "courseId", "completedAt",
        "attendanceRate", "completedMinutes");
    private static final List<String> SUBJECT_FIELDS_WITH_CREDIT = List.of(
        "id", "completionId", "enrollmentId", "courseId", "completedAt",
        "attendanceRate", "completedMinutes", "creditValue");
    private static final List<String> STATUS_FIELDS = List.of("id", "type");
    private static final List<String> BITSTRING_STATUS_FIELDS = List.of(
        "id", "type", "statusPurpose", "statusListIndex", "statusListCredential");
    private static final List<String> STATUS_LIST_ROOT_FIELDS = List.of(
        "@context", "id", "type", "issuer", "validFrom", "credentialSubject");
    private static final List<String> STATUS_LIST_SUBJECT_FIELDS = List.of(
        "id", "type", "statusPurpose", "encodedList");
    private static final Pattern STATUS_LIST_INDEX = Pattern.compile("^(0|[1-9][0-9]{0,9})$");
    private static final Pattern ENCODED_LIST = Pattern.compile("^u[A-Za-z0-9_-]+$");
    private static final List<String> HEADER_FIELDS = List.of("alg", "cty", "kid", "typ");

    private final String legacyKeyId;
    private final CredentialUriProvider uriProvider;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final ObjectMapper objectMapper;
    private final ObjectReader verificationReader;

    public Ed25519JoseCredentialProofService(
        String legacyKeyId,
        CredentialUriProvider uriProvider,
        PrivateKey privateKey,
        PublicKey publicKey,
        ObjectMapper objectMapper
    ) {
        this.legacyKeyId = requireText(legacyKeyId, "legacyKeyId");
        this.uriProvider = Objects.requireNonNull(uriProvider, "uriProvider");
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.verificationReader = this.objectMapper.reader()
            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }

    @Override
    public SignedCredentialEnvelope sign(CredentialDocument document) {
        try {
            Objects.requireNonNull(document, "document");
            CredentialProfile profile = validateCanonicalPayload(document.canonicalJson());
            if (profile.legacy()) {
                throw invalid();
            }
            return signWithKey(profile.expectedKeyId(), document);
        } catch (Exception exception) {
            throw generationFailed();
        }
    }

    @Override
    public SignedCredentialEnvelope signStatusList(CredentialDocument document) {
        try {
            Objects.requireNonNull(document, "document");
            return signWithKey(
                validateCanonicalStatusListPayload(document.canonicalJson()), document);
        } catch (Exception exception) {
            throw generationFailed();
        }
    }

    private SignedCredentialEnvelope signWithKey(String keyId, CredentialDocument document)
        throws Exception {
        String canonicalHeader = canonicalHeader(keyId);
        String encodedHeader = encode(canonicalHeader.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = encode(document.utf8Bytes());
        String signingInput = encodedHeader + "." + encodedPayload;
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return new SignedCredentialEnvelope(
            MEDIA_TYPE, signingInput + "." + encode(signer.sign()));
    }

    @Override
    public CredentialDocument verify(SignedCredentialEnvelope envelope) {
        return verifyWith(envelope, payload -> validateCanonicalPayload(payload).expectedKeyId());
    }

    @Override
    public CredentialDocument verifyStatusList(SignedCredentialEnvelope envelope) {
        return verifyWith(envelope, this::validateCanonicalStatusListPayload);
    }

    private CredentialDocument verifyWith(
        SignedCredentialEnvelope envelope,
        PayloadValidator validator
    ) {
        try {
            Objects.requireNonNull(envelope, "envelope");
            if (!MEDIA_TYPE.equals(envelope.mediaType())) {
                throw invalid();
            }
            String[] segments = envelope.compactJws().split("\\.", -1);
            if (segments.length != 3) {
                throw invalid();
            }
            for (String segment : segments) {
                requireCanonicalBase64Url(segment);
            }

            String headerText = decodeUtf8(segments[0]);
            String headerKeyId = validateCanonicalHeader(headerText);
            byte[] signatureBytes = decode(segments[2]);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update((segments[0] + "." + segments[1])
                .getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signatureBytes)) {
                throw invalid();
            }

            String payload = decodeUtf8(segments[1]);
            String expectedKeyId = validator.expectedKeyId(payload);
            if (!expectedKeyId.equals(headerKeyId)
                || !canonicalHeader(headerKeyId).equals(headerText)
                || !encode(headerText.getBytes(StandardCharsets.UTF_8)).equals(segments[0])) {
                throw invalid();
            }
            return new CredentialDocument(payload);
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private String validateCanonicalHeader(String header) throws Exception {
        JsonNode node = verificationReader.readTree(header);
        if (node == null || !node.isObject()) {
            throw invalid();
        }
        requireExactFields(node, HEADER_FIELDS);
        if (!"EdDSA".equals(requireTextNode(node, "alg"))
            || !"vc".equals(requireTextNode(node, "cty"))
            || !"vc+jwt".equals(requireTextNode(node, "typ"))) {
            throw invalid();
        }
        String keyId = requireTextNode(node, "kid");
        if (!header.equals(canonicalHeader(keyId))) {
            throw invalid();
        }
        return keyId;
    }

    private CredentialProfile validateCanonicalPayload(String payload) throws Exception {
        JsonNode parsed = parseCanonicalObject(payload);
        CredentialProfile profile = validateCredentialPayload(parsed);
        requireCanonicalSerialization(parsed, payload);
        return profile;
    }

    private String validateCanonicalStatusListPayload(String payload) throws Exception {
        JsonNode parsed = parseCanonicalObject(payload);
        String expectedKeyId = validateStatusListPayload(parsed);
        requireCanonicalSerialization(parsed, payload);
        return expectedKeyId;
    }

    private JsonNode parseCanonicalObject(String payload) throws Exception {
        JsonNode parsed = verificationReader.readTree(payload);
        if (parsed == null || !parsed.isObject()) {
            throw invalid();
        }
        return parsed;
    }

    private void requireCanonicalSerialization(JsonNode parsed, String payload) throws Exception {
        String reserialized = objectMapper.writer()
            .without(SerializationFeature.INDENT_OUTPUT,
                SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsString(parsed);
        if (!payload.equals(reserialized)) {
            throw invalid();
        }
    }

    /** BitstringStatusListCredential 화이트리스트. 비트 배열 외 어떤 claim 도 허용하지 않는다. */
    private String validateStatusListPayload(JsonNode root) {
        requireExactFields(root, STATUS_LIST_ROOT_FIELDS);
        requireExactArray(root.get("@context"), W3C_CONTEXT);
        String listUrl = requireTextNode(root, "id");
        requireStatusListId(listUrl);
        requireExactArray(root.get("type"),
            "VerifiableCredential", "BitstringStatusListCredential");
        UUID institutionId = requireIssuer(root);
        requireCanonicalInstant(root, "validFrom");

        JsonNode subject = root.get("credentialSubject");
        if (subject == null || !subject.isObject()) {
            throw invalid();
        }
        requireExactFields(subject, STATUS_LIST_SUBJECT_FIELDS);
        if (!(listUrl + "#list").equals(requireTextNode(subject, "id"))
            || !"BitstringStatusList".equals(requireTextNode(subject, "type"))
            || !"revocation".equals(requireTextNode(subject, "statusPurpose"))
            || !ENCODED_LIST.matcher(requireTextNode(subject, "encodedList")).matches()) {
            throw invalid();
        }
        return uriProvider.keyId(institutionId);
    }

    private UUID requireStatusListId(String listUrl) {
        int separator = listUrl.lastIndexOf('/');
        if (separator < 0 || separator == listUrl.length() - 1) {
            throw invalid();
        }
        String idText = listUrl.substring(separator + 1);
        if (!UUID_TEXT.matcher(idText).matches()) {
            throw invalid();
        }
        UUID listId = UUID.fromString(idText);
        if (!uriProvider.statusListUrl(listId).equals(listUrl)) {
            throw invalid();
        }
        return listId;
    }

    private CredentialProfile validateCredentialPayload(JsonNode root) {
        JsonNode context = root.get("@context");
        boolean legacy = context != null && context.isArray() && context.size() == 1;
        boolean hasValidUntil = root.get("validUntil") != null;
        if (legacy) {
            requireExactFields(root,
                hasValidUntil ? LEGACY_ROOT_FIELDS_WITH_VALID_UNTIL : LEGACY_ROOT_FIELDS);
            requireExactArray(context, W3C_CONTEXT);
        } else {
            requireExactFields(root,
                hasValidUntil ? W3C_ROOT_FIELDS_WITH_VALID_UNTIL : W3C_ROOT_FIELDS);
            requireExactArray(context, W3C_CONTEXT, uriProvider.contextUrl());
        }
        requireUrn(root, "id", "urn:uuid:");
        requireExactArray(root.get("type"),
            "VerifiableCredential", "LifelongEducationCompletionCredential");

        String expectedKeyId;
        if (legacy) {
            requireUrn(root, "issuer", "urn:dabaeum:institution:");
            expectedKeyId = legacyKeyId;
        } else {
            UUID institutionId = requireIssuer(root);
            expectedKeyId = uriProvider.keyId(institutionId);
            validateStatus(root.get("credentialStatus"));
        }
        Instant validFrom = requireCanonicalInstant(root, "validFrom");
        if (hasValidUntil) {
            Instant validUntil = requireCanonicalInstant(root, "validUntil");
            if (!validFrom.isBefore(validUntil)) {
                throw invalid();
            }
        }

        JsonNode subject = root.get("credentialSubject");
        if (subject == null || !subject.isObject()) {
            throw invalid();
        }
        boolean hasCredit = subject.get("creditValue") != null;
        requireExactFields(subject, hasCredit ? SUBJECT_FIELDS_WITH_CREDIT : SUBJECT_FIELDS);
        requireUrn(subject, "id", "urn:dabaeum:user:");
        requireUuid(subject, "completionId");
        requireUuid(subject, "enrollmentId");
        requireUuid(subject, "courseId");
        requireCanonicalInstant(subject, "completedAt");

        BigDecimal attendanceRate = requireScaleTwoDecimal(subject, "attendanceRate");
        if (attendanceRate.signum() < 0
            || attendanceRate.compareTo(new BigDecimal("100.00")) > 0) {
            throw invalid();
        }
        JsonNode completedMinutes = subject.get("completedMinutes");
        if (completedMinutes == null || !completedMinutes.isIntegralNumber()
            || !completedMinutes.canConvertToInt() || completedMinutes.intValue() < 0) {
            throw invalid();
        }
        if (hasCredit && requireScaleTwoDecimal(subject, "creditValue").signum() < 0) {
            throw invalid();
        }
        return new CredentialProfile(legacy, expectedKeyId);
    }

    private UUID requireIssuer(JsonNode root) {
        String issuer = requireTextNode(root, "issuer");
        int separator = issuer.lastIndexOf('/');
        if (separator < 0 || separator == issuer.length() - 1) {
            throw invalid();
        }
        String idText = issuer.substring(separator + 1);
        if (!UUID_TEXT.matcher(idText).matches()) {
            throw invalid();
        }
        UUID institutionId = UUID.fromString(idText);
        if (!uriProvider.issuerUrl(institutionId).equals(issuer)) {
            throw invalid();
        }
        return institutionId;
    }

    private void validateStatus(JsonNode status) {
        if (status == null || !status.isObject()) {
            throw invalid();
        }
        List<String> fields = List.copyOf(status.propertyNames());
        if (fields.equals(STATUS_FIELDS)) {
            validateLegacyStatus(status);
        } else if (fields.equals(BITSTRING_STATUS_FIELDS)) {
            validateBitstringStatus(status);
        } else {
            throw invalid();
        }
    }

    /** W3C Bitstring Status List v1.0 항목. 리스트 URL·인덱스·id 가 서로 맞아야 한다. */
    private void validateBitstringStatus(JsonNode status) {
        String listUrl = requireTextNode(status, "statusListCredential");
        requireStatusListId(listUrl);
        String index = requireTextNode(status, "statusListIndex");
        if (!STATUS_LIST_INDEX.matcher(index).matches()
            || Long.parseLong(index) > Integer.MAX_VALUE
            || !"BitstringStatusListEntry".equals(requireTextNode(status, "type"))
            || !"revocation".equals(requireTextNode(status, "statusPurpose"))
            || !(listUrl + "#" + index).equals(requireTextNode(status, "id"))) {
            throw invalid();
        }
    }

    /** 전환 이전 발급분. 수료증 번호별 상태 URL 을 가리키는 자체 확장 타입을 계속 받아준다. */
    private void validateLegacyStatus(JsonNode status) {
        String statusUrl = requireTextNode(status, "id");
        int separator = statusUrl.lastIndexOf('/');
        if (separator < 0 || separator == statusUrl.length() - 1) {
            throw invalid();
        }
        String credentialNo = statusUrl.substring(separator + 1);
        if (!CREDENTIAL_NUMBER.matcher(credentialNo).matches()
            || !uriProvider.statusUrl(credentialNo).equals(statusUrl)
            || !"DabaeumCredentialStatus".equals(requireTextNode(status, "type"))) {
            throw invalid();
        }
    }

    private String canonicalHeader(String keyId) {
        try {
            Map<String, String> header = new LinkedHashMap<>();
            header.put("alg", "EdDSA");
            header.put("cty", "vc");
            header.put("kid", requireText(keyId, "keyId"));
            header.put("typ", "vc+jwt");
            return objectMapper.writer()
                .without(SerializationFeature.INDENT_OUTPUT,
                    SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(header);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private void requireExactFields(JsonNode node, List<String> expectedFields) {
        if (!List.copyOf(node.propertyNames()).equals(expectedFields)) {
            throw invalid();
        }
    }

    private void requireExactArray(JsonNode node, String... expectedValues) {
        if (node == null || !node.isArray() || node.size() != expectedValues.length) {
            throw invalid();
        }
        for (int index = 0; index < expectedValues.length; index++) {
            JsonNode value = node.get(index);
            if (value == null || !value.isString()
                || !expectedValues[index].equals(value.asString())) {
                throw invalid();
            }
        }
    }

    private void requireUrn(JsonNode node, String fieldName, String prefix) {
        String value = requireTextNode(node, fieldName);
        if (!value.startsWith(prefix)
            || !UUID_TEXT.matcher(value.substring(prefix.length())).matches()) {
            throw invalid();
        }
    }

    private void requireUuid(JsonNode node, String fieldName) {
        if (!UUID_TEXT.matcher(requireTextNode(node, fieldName)).matches()) {
            throw invalid();
        }
    }

    private Instant requireCanonicalInstant(JsonNode node, String fieldName) {
        String value = requireTextNode(node, fieldName);
        try {
            Instant instant = Instant.parse(value);
            if (!instant.toString().equals(value)) {
                throw invalid();
            }
            return instant;
        } catch (DateTimeParseException exception) {
            throw invalid();
        }
    }

    private BigDecimal requireScaleTwoDecimal(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isNumber() || value.isIntegralNumber()) {
            throw invalid();
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.scale() != 2) {
            throw invalid();
        }
        return decimal;
    }

    private String requireTextNode(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw invalid();
        }
        return value.asString();
    }

    private void requireCanonicalBase64Url(String segment) {
        if (!BASE64URL.matcher(segment).matches()) {
            throw invalid();
        }
        byte[] decoded = decode(segment);
        if (!segment.equals(encode(decoded))) {
            throw invalid();
        }
    }

    private String decodeUtf8(String segment) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(decode(segment)))
            .toString();
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private ProofException generationFailed() {
        return new ProofException(FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED,
            "Credential proof generation failed");
    }

    private ProofException invalid() {
        return new ProofException(FailureCode.CREDENTIAL_PROOF_INVALID,
            "Credential proof is invalid");
    }

    private record CredentialProfile(boolean legacy, String expectedKeyId) {
    }

    @FunctionalInterface
    private interface PayloadValidator {

        String expectedKeyId(String payload) throws Exception;
    }
}
