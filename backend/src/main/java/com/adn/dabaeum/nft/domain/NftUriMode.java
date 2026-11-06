package com.adn.dabaeum.nft.domain;

/**
 * 토큰의 uri 칸에 무엇을 적는가 (docs/specs/daeguchain-nft-issuance.md 4절).
 *
 * <p>대구시 게이트웨이는 요청 본문의 웹 주소 중 허용 목록(www.daegu.go.kr 등) 밖의 것을 막는다(2026-10-28 실측).
 * 운영 도메인이 허용되기 전에는 {@link #URN} 으로 발행한다.
 */
public enum NftUriMode {
    /** {@code urn:dabaeum:nft:<badges|certificates>:<장부 ID>}. 웹 주소가 아니라 게이트웨이를 통과한다. */
    URN,
    /** {@code https://<도메인>/api/v1/public/nft/...}. 지갑이 메타데이터를 바로 연다. 도메인 허용이 필요하다. */
    URL
}
