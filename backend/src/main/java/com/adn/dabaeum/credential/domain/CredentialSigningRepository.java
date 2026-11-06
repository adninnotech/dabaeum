package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 서명 입력 사전 커밋과 조건부 서명 저장 (설계서 §8.1 5·8단계). 쓰기는 모두 갱신 행 수로 결과를 돌려준다. */
public interface CredentialSigningRepository {

    Optional<CredentialSigningState> findSigningState(UUID credentialId);

    /** 수료증 → 그룹 → 이수 → 수강 → 과정의 기관. */
    Optional<UUID> findInstitutionId(UUID credentialId);

    /**
     * 5단계. ISSUING 이고 봉투도 헤더도 없는 행에만 첫 입력을 쓴다 (sign_attempt_no=1). issuer·subject·
     * valid_from·issued_at 도 함께 고정한다 — 재시도가 다른 시각으로 문서를 만들어도 이 행의 값을 쓴다.
     *
     * @return 썼으면 true. 다른 워커가 먼저 커밋했거나 상태가 바뀌었으면 false
     */
    boolean commitSigningInput(
        UUID credentialId, UUID signKeyId, int signKeyVersion, CredentialSigningInput input,
        String issuerIdentifier, String subjectIdentifier, Instant issuedAt, Instant now);

    /**
     * 버전 거부 뒤 같은 행에서 키 참조와 헤더만 바꾸고 시도 번호를 하나 올린다. payload 는 같아야 한다.
     *
     * @return 썼으면 true. 시도 번호나 payload 가 어긋나면 false
     */
    boolean recommitHeader(
        UUID credentialId, int expectedAttemptNo, UUID signKeyId, int signKeyVersion,
        CredentialSigningInput input, Instant now);

    /**
     * 8단계. 시도 번호·키 참조·두 조각·상태가 모두 같고 봉투가 없을 때만 봉투와 해시를 쓴다.
     *
     * @return 썼으면 true. 0행이면 오래된 응답이다
     */
    boolean storeSignature(
        UUID credentialId, int attemptNo, UUID signKeyId, int signKeyVersion,
        CredentialSigningInput input, String vcPayload, String vcHash, Instant now);
}
