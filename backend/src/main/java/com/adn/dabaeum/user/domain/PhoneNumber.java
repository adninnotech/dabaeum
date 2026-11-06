package com.adn.dabaeum.user.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/** 휴대전화 비교용 정규형. 숫자만 남기며 하이픈·공백 표기 차이를 없앤다. */
public final class PhoneNumber {

    private static final Pattern FORMAT = Pattern.compile("^[0-9][0-9 -]{7,18}[0-9]$");
    private static final int MIN_DIGITS = 9;
    private static final int MAX_DIGITS = 11;

    private PhoneNumber() {
    }

    public static Optional<String> digits(String value) {
        if (value == null || !FORMAT.matcher(value.strip()).matches()) {
            return Optional.empty();
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.length() < MIN_DIGITS || digits.length() > MAX_DIGITS ? Optional.empty() : Optional.of(digits);
    }
}
