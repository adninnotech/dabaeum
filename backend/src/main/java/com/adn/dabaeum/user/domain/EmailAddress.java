package com.adn.dabaeum.user.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 이메일 정규형: 앞뒤 공백 제거, 소문자.
 * {@link #normalized} 는 조회(로그인·재설정·중복 확인)용으로 느슨하고, 새로 저장하는 값은 {@link #deliverable} 로
 * 도메인에 점이 있는지까지 본다(a@b 거부). 요청 DTO 는 {@code @Email(regexp = EmailAddress.PATTERN)} 으로 같은 규칙을 쓴다.
 */
public final class EmailAddress {

    /** 공백·@ 없는 로컬부 @ 빈 칸 없는 라벨 둘 이상(점으로 구분). 프론트 EMAIL 정규식보다 빈 라벨만 더 막는다. */
    public static final String PATTERN = "^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$";

    private static final Pattern DELIVERABLE = Pattern.compile(PATTERN);
    private static final int MAX_LENGTH = 320;

    private EmailAddress() {
    }

    public static Optional<String> normalized(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        boolean wellFormed = !value.isBlank()
            && value.length() <= MAX_LENGTH
            && at >= 1
            && at < value.length() - 1
            && value.indexOf('@', at + 1) < 0;
        return wellFormed ? Optional.of(value) : Optional.empty();
    }

    /** 새로 저장할 이메일. 정규형이면서 도메인에 점이 있어야 한다. */
    public static Optional<String> deliverable(String raw) {
        return normalized(raw).filter(value -> DELIVERABLE.matcher(value).matches());
    }
}
