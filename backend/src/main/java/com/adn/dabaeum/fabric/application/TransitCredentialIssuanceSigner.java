package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.application.CredentialSigningKeyGate;
import com.adn.dabaeum.credential.application.SigningKeySelection;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningException;
import com.adn.dabaeum.credential.domain.CredentialSigningException.Disposition;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialSigningInputFactory;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningState;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.TransitSignature;
import com.adn.dabaeum.credential.domain.TransitSigningException;
import com.adn.dabaeum.credential.domain.TransitSigningPort;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * openbao provider 의 수료증 서명 (설계서 §7.3, §7.4, §8.1 2~8단계). 외부 호출은 트랜잭션 밖이고 DB 쓰기는
 * 저장소의 조건부 문장 하나씩이다. 결정적 서명이라 같은 입력·버전의 재호출은 무해하다.
 */
public final class TransitCredentialIssuanceSigner implements CredentialIssuanceSigner {

    /** 한 번의 처리에서 허용하는 버전 거부 뒤 재커밋 횟수. 회전은 드물어 두 번이면 충분하다. */
    static final int MAX_VERSION_RECOMMITS = 2;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CredentialSigningRepository signing;
    private final CredentialRepository credentials;
    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final CredentialSigningKeyGate gate;
    private final CredentialSigningInputFactory inputs;
    private final TransitSigningPort transit;
    private final CredentialHashService hashes;

    public TransitCredentialIssuanceSigner(
        CredentialSigningRepository signing,
        CredentialRepository credentials,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        CredentialSigningKeyGate gate,
        CredentialSigningInputFactory inputs,
        TransitSigningPort transit,
        CredentialHashService hashes
    ) {
        this.signing = Objects.requireNonNull(signing, "signing");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.transit = Objects.requireNonNull(transit, "transit");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
    }

    @Override
    public Signed sign(
        UUID credentialId,
        Supplier<CredentialFabricIssuanceContext> context,
        Function<SignedCredentialEnvelope, String> vcHash,
        Instant now
    ) {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(vcHash, "vcHash");
        Objects.requireNonNull(now, "now");
        CredentialSigningState state = requireIssuing(credentialId);
        if (state.vcPayload() != null) {
            return stored(credentialId);
        }
        UUID institutionId = signing.findInstitutionId(credentialId)
            .orElseThrow(() -> new IllegalStateException("Credential institution is missing"));
        if (!state.inputCommitted()) {
            state = commitFirstInput(credentialId, institutionId, context, now);
        }
        for (int recommits = 0; ; recommits++) {
            TransitSignature signature;
            try {
                signature = signWith(state);
            } catch (TransitSigningException exception) {
                if (exception.reason() == TransitSigningException.Reason.UNAVAILABLE) {
                    throw new CredentialSigningException(
                        CredentialSigningException.UNAVAILABLE, Disposition.RETRY, exception);
                }
                if (exception.reason() == TransitSigningException.Reason.REJECTED) {
                    throw new CredentialSigningException(
                        CredentialSigningException.REJECTED, Disposition.FAIL, exception);
                }
                if (recommits >= MAX_VERSION_RECOMMITS) {
                    throw new CredentialSigningException(
                        CredentialSigningException.VERSION_REJECTED, Disposition.FAIL, exception);
                }
                state = recommitCurrentVersion(credentialId, institutionId, state, exception, now);
                continue;
            }
            return store(credentialId, state, signature, vcHash, now);
        }
    }

    private CredentialSigningState commitFirstInput(
        UUID credentialId,
        UUID institutionId,
        Supplier<CredentialFabricIssuanceContext> context,
        Instant now
    ) {
        SigningKeySelection selection = gate.select(institutionId);
        CredentialFabricIssuanceContext resolved = context.get();
        CredentialSigningInput input = inputs.credentialSigningInput(
            resolved.document(), institutionId, selection.keyId());
        signing.commitSigningInput(credentialId, selection.master().id(), selection.version().keyVersion(),
            input, resolved.issuerIdentifier(), resolved.subjectIdentifier(), resolved.issuedAt(), now);
        // 커밋에 졌어도 이긴 쪽의 입력으로 이어간다. 입력이 없으면 상태가 바뀐 것이다.
        CredentialSigningState state = requireIssuing(credentialId);
        if (!state.inputCommitted()) {
            throw stale();
        }
        return state;
    }

    private TransitSignature signWith(CredentialSigningState state) {
        InstitutionSignKey master = keys.findById(state.signKeyId())
            .orElseThrow(() -> new IllegalStateException("Signing key is missing"));
        return transit.sign(master.mount(), master.keyName(), state.signKeyVersion(), state.input().bytes());
    }

    /** §8.1: min_encryption_version 거부 뒤 같은 행에서 새 current_version 으로 헤더만 다시 커밋한다. */
    private CredentialSigningState recommitCurrentVersion(
        UUID credentialId,
        UUID institutionId,
        CredentialSigningState state,
        TransitSigningException cause,
        Instant now
    ) {
        SigningKeySelection selection = gate.select(institutionId);
        if (selection.master().id().equals(state.signKeyId())
            && selection.version().keyVersion() <= state.signKeyVersion()) {
            // DB 현재 버전이 오르지 않았는데 거부됐다. 회전 T2 전에 min_encryption_version 이 올랐다 (§6.2 위반).
            throw new CredentialSigningException(
                CredentialSigningException.VERSION_REJECTED, Disposition.FAIL, cause);
        }
        CredentialSigningInput input = inputs.credentialSigningInput(
            state.input().document(), institutionId, selection.keyId());
        if (!input.encodedPayload().equals(state.vcJwsPayload())) {
            throw new IllegalStateException("Signing payload changed on header recommit");
        }
        if (!signing.recommitHeader(credentialId, state.signAttemptNo(), selection.master().id(),
            selection.version().keyVersion(), input, now)) {
            throw stale();
        }
        return requireIssuing(credentialId);
    }

    private Signed store(
        UUID credentialId,
        CredentialSigningState state,
        TransitSignature signature,
        Function<SignedCredentialEnvelope, String> vcHash,
        Instant now
    ) {
        // §7.3 4중 일치: 헤더와 DB 는 state 의 버전으로 만들었다. 요청도 그 버전이었다. 응답만 보면 된다.
        if (signature.keyVersion() != state.signKeyVersion()) {
            throw new CredentialSigningException(CredentialSigningException.VERSION_MISMATCH, Disposition.FAIL);
        }
        // 서명 사이에 유출 선언이 커밋됐으면 원장에 올리지 않는다 (§20 유출 경합).
        SignKeyVersionStatus status = versions.findBySignKeyIdAndVersion(state.signKeyId(), state.signKeyVersion())
            .map(InstitutionSignKeyVersion::status)
            .orElseThrow(() -> new IllegalStateException("Signing key version is missing"));
        if (status == SignKeyVersionStatus.COMPROMISED) {
            throw new CredentialSigningException(CredentialSigningException.KEY_COMPROMISED, Disposition.FAIL);
        }
        SignedCredentialEnvelope envelope = state.input().envelope(signature.signature());
        String hash = Objects.requireNonNull(vcHash.apply(envelope), "vcHash");
        if (!signing.storeSignature(credentialId, state.signAttemptNo(), state.signKeyId(),
            state.signKeyVersion(), state.input(), hashes.serialize(envelope), hash, now)) {
            throw stale();
        }
        return stored(credentialId);
    }

    private CredentialSigningState requireIssuing(UUID credentialId) {
        CredentialSigningState state = signing.findSigningState(credentialId)
            .orElseThrow(() -> new IllegalStateException("Credential signing target is missing"));
        if (state.status() != CredentialStatus.ISSUING) {
            throw stale();
        }
        return state;
    }

    private Signed stored(UUID credentialId) {
        Credential credential = credentials.findById(credentialId)
            .orElseThrow(() -> new IllegalStateException("Credential signing target is missing"));
        if (credential.status() != CredentialStatus.ISSUING || credential.vcPayload() == null) {
            throw stale();
        }
        try {
            JsonNode node = JSON.readTree(credential.vcPayload());
            return new Signed(credential, new SignedCredentialEnvelope(
                node.get("mediaType").asString(), node.get("compactJws").asString()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored credential envelope is invalid");
        }
    }

    private static CredentialSigningException stale() {
        return new CredentialSigningException(CredentialSigningException.STALE, Disposition.STALE);
    }
}
