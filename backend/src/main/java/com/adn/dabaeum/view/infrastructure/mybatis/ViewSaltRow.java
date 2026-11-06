package com.adn.dabaeum.view.infrastructure.mybatis;

/** byte[] 를 직접 돌려주면 MyBatis 가 여러 행 결과로 착각한다. 한 겹 감싼다. */
public record ViewSaltRow(byte[] salt) {
}
