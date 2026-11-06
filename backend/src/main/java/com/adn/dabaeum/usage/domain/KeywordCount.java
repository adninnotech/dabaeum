package com.adn.dabaeum.usage.domain;

/** 검색어 순위 한 줄. searchers 는 순위 종류에 따라 검색한 사람 수 또는 결과 0건이었던 사람 수. */
public record KeywordCount(String keyword, long searchers) {
}
