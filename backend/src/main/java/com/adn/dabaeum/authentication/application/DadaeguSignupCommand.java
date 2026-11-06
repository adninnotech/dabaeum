package com.adn.dabaeum.authentication.application;

import java.util.List;

public record DadaeguSignupCommand(
    String registrationToken,
    List<String> interestCodes,
    Boolean agreedToInterest,
    Boolean agreedToUsage,
    Boolean agreedToPrivacy,
    Boolean agreedToMarketing
) {
}
