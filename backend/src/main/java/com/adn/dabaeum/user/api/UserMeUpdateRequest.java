package com.adn.dabaeum.user.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public class UserMeUpdateRequest extends UserUpdateRequest {

    public UserMeUpdateRequest() {
        super();
    }
}
