package com.adn.dabaeum.nft.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NftIssuanceRepository {

    /** 대상에 장부가 없을 때만 넣는다. 넣었으면 true. */
    boolean insertIfAbsent(NftIssuance issuance);

    Optional<NftIssuance> findById(UUID id);

    /** 보낼 차례인 대기 건을 잠그고 전송 중으로 바꿔 돌려준다. */
    List<NftIssuance> claimDue(int limit, Instant now, String contractForBadge, String contractForCredential);

    /** 확정을 기다리는 전송 건. */
    List<NftIssuance> findSubmitted(int limit);

    /** 지정 시각보다 오래 전송 중으로 남은 건. 워커가 hash 를 적기 전에 멈춘 경우다. */
    List<NftIssuance> findStaleSubmitting(Instant before, int limit);

    List<NftIssuance> findWaitingDomain(int limit);

    /** 기대 상태일 때만 바꾼다. 다른 워커가 먼저 바꿨으면 false. */
    boolean update(NftIssuanceStatus expected, NftIssuance next);

    /** 진척이 없는 건의 갱신 시각만 바꿔, 확인 순서에서 뒤로 보낸다. 기대 상태일 때만. */
    boolean touch(UUID id, NftIssuanceStatus expected, Instant now);

    /** 폐기·대체된 대상의 장부에 폐기 시각을 적는다. 적은 건수. */
    int markRevokedTargets(Instant now);

    /** 폐기됐는데 아직 보내지 않은 건을 취소한다. 취소한 건수. */
    int cancelRevokedBeforeSubmit(Instant now);
}
