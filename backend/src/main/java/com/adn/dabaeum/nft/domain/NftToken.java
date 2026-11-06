package com.adn.dabaeum.nft.domain;

/** 체인에 기록된 토큰 1개. */
public record NftToken(String contractAddress, long index, String owner, String uri, String hash, boolean active) {
}
