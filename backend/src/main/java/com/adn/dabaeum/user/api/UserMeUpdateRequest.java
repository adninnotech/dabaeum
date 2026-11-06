package com.adn.dabaeum.user.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public class UserMeUpdateRequest extends UserUpdateRequest {

    private List<String> interestCodes;
    private Boolean agreedToInterest;

    public UserMeUpdateRequest() {
        super();
    }

    @JsonSetter("interestCodes")
    public void setInterestCodes(List<String> value) {
        interestCodes = value == null ? null : List.copyOf(value);
    }

    @JsonSetter("agreedToInterest")
    public void setAgreedToInterest(Boolean value) {
        agreedToInterest = value;
    }

    /** 보내지 않으면 null 이고 관심 분야를 건드리지 않는다. */
    public List<String> interestCodes() {
        return interestCodes;
    }

    public Boolean agreedToInterest() {
        return agreedToInterest;
    }
}
