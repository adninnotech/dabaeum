package com.adn.dabaeum.administrator.api;

import java.util.UUID;

/**
 * 생성 응답 본문. userId 를 맨 앞에 둔 것은 접속기록 어드바이스가 data.userId 로 대상을 읽기 때문이다.
 * temporaryPassword 는 이 응답에서 한 번만 보인다.
 */
public record AdministratorCreatedData(UUID userId, AdministratorResponse administrator, String temporaryPassword) {

    @Override
    public String toString() {
        return "AdministratorCreatedData[userId=" + userId + ", temporaryPassword=***]";
    }
}
