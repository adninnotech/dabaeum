package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;

public interface LocalAccountApplicationService {

    LocalAuthResult signup(SignupCommand command);

    LocalAuthResult login(LoginCommand command);

    /** 로그인된 사용자에게 LOCAL(email+password) 로그인 수단을 붙인다. 이미 있으면 409. */
    LocalAccountCredential linkLocal(LinkLocalAccountCommand command);

    /**
     * 로그인한 본인이 현재 비밀번호를 확인시키고 새 비밀번호로 바꾼다.
     * 다대구 DID 전용 계정은 로컬 자격증명이 없으므로 404.
     */
    void changePassword(ChangePasswordCommand command);
}
