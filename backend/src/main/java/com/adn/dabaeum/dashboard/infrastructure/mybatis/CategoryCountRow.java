package com.adn.dabaeum.dashboard.infrastructure.mybatis;

/** 기관 내 카테고리 하나의 강좌 수. category 가 없으면 SQL 이 '미분류' 로 채운다. */
public record CategoryCountRow(
    String category,
    Long courseCount
) {
}
