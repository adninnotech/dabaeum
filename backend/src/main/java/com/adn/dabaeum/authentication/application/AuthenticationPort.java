package com.adn.dabaeum.authentication.application;

import org.springframework.security.core.Authentication;

public interface AuthenticationPort {

    Authentication authenticate(String token);
}
