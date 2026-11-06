package com.adn.dabaeum.credential.domain;

import java.util.List;
import java.util.UUID;

/** 전환 이전 발급분을 버전 0 행에 연결한다 (설계서 §11.3 3번). 모든 문장은 멱등이다. */
public interface LegacySignKeyBackfillRepository {

    /** 서명 참조가 없는 발급 상태 수료증 또는 Status List 를 하나라도 가진 기관. */
    List<UUID> findInstitutionsWithUnlinkedRows();

    /**
     * 기관의 발급 상태 수료증 중 sign_key_id 가 없는 행을 버전 0 으로 연결하고, 봉투 JSON 의
     * compactJws 에서 vc_jws_header·vc_jws_payload 를 채운다.
     *
     * @return 갱신한 행 수
     */
    int linkCredentials(UUID institutionId, UUID signKeyId);

    /** @return 갱신한 행 수 */
    int linkStatusLists(UUID institutionId, UUID signKeyId);
}
