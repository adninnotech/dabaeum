package com.adn.dabaeum.accesslog.domain;

/** 목록 한 줄. 행위자 이름은 조회 시점의 이름이다(계정이 없어졌으면 null). */
public record AdminAccessLogEntry(AdminAccessLog log, String actorName) {
}
