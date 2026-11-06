package com.adn.dabaeum.administrator.application;

import com.adn.dabaeum.administrator.domain.Administrator;

/** 생성 결과. 임시 비밀번호는 응답에서 한 번만 보인다 — 실수로 로그에 찍혀도 원문이 나가지 않게 toString 에서 가린다. */
public record CreatedAdministrator(Administrator administrator, String temporaryPassword) {

    @Override
    public String toString() {
        return "CreatedAdministrator[administrator=" + administrator + ", temporaryPassword=***]";
    }
}
