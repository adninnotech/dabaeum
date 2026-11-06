package com.adn.dabaeum.nft.domain;

import java.util.List;
import java.util.Optional;

/**
 * NFT 원장(대구체인 NFT 721) 호출. 발행 서명에 쓰는 발급 지갑 키는 구현이 설정에서 읽고,
 * 도메인 객체로는 넘기지 않는다.
 */
public interface NftLedgerPort {

    /** 토큰 1개 발행. 체인 처리 결과가 늦으면 fact hash 만 담긴 대기 상태로 돌아온다. */
    NftMintSubmission mint(NftMintRequest request);

    /** fact hash 로 처리 여부를 읽는다. 아직 체인에 없으면 대기 상태다. */
    NftTransactionState transaction(String factHash);

    /** 발행 fact hash 로 새로 생긴 토큰 번호들. mint 응답에는 번호가 없다. */
    List<Long> mintedTokenIndexes(String contractAddress, String factHash);

    /** 토큰 번호의 체인 기록. 없으면 empty. */
    Optional<NftToken> token(String contractAddress, long index);

    /** 컨트랙트의 총 발행 수. */
    long totalSupply(String contractAddress);

    /** 지갑 잔액. 단위·소수 자릿수는 체인이 준 문자열 그대로다. */
    List<NftAmount> balance(String address);
}
