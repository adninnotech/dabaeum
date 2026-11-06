package com.adn.dabaeum.nft.domain;

/** 잔액 한 줄. 금액은 체인이 준 문자열 그대로 둔다(단위·소수 자릿수 미확인). */
public record NftAmount(String amount, String currency) {
}
