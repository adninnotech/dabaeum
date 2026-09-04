package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;

public interface LocalAccountApplicationService {

    LocalAuthResult signup(SignupCommand command);

    LocalAuthResult login(LoginCommand command);

    /** 로그인된 사용자에게 LOCAL(email+password) 로그인 수단을 붙인다. 이미 있으면 409. */
    LocalAccountCredential linkLocal(LinkLocalAccountCommand command);
}
