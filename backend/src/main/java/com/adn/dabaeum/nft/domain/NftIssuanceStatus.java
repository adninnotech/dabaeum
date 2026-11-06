package com.adn.dabaeum.nft.domain;

/** 발행 장부 한 줄의 체인 처리 단계. */
public enum NftIssuanceStatus {
    /** 메타데이터 도메인이 설정되지 않아 발행을 미룬다. */
    WAITING_DOMAIN,
    /** 보낼 차례를 기다린다. 체인에는 아무것도 없다. */
    PENDING,
    /** 워커가 집어 mint 를 보내는 중이다. 이 상태로 오래 남으면 결과를 모른다. */
    SUBMITTING,
    /** mint 를 보냈고 fact hash 를 받았다. 리컨실러가 확정을 기다린다. */
    SUBMITTED,
    /** 체인에 반영되고 토큰 번호·내용을 검증했다. */
    MINTED,
    /** 체인이 처리하지 않았다. 체인에 아무것도 없으므로 운영자가 원인을 고친 뒤 다시 대기로 돌릴 수 있다. */
    FAILED,
    /** 보냈는지, 체인에 생겼는지 모른다. 자동으로 다시 보내지 않고 운영자가 판정한다. */
    UNKNOWN,
    /** 보내기 전에 대상이 폐기돼 발행하지 않는다. */
    CANCELLED
}
