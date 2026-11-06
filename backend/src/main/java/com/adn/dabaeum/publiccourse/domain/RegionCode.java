package com.adn.dabaeum.publiccourse.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 공공강좌 지역 필터의 구·군 코드. 법정 시군구코드(27xxx)의 3~4번째 자리다.
 * 회원 정보가 아니다(회원 구·군은 2026-11-03 제거, docs/specs/interest-based-member-profile.md).
 */
public enum RegionCode {
    JUNG("11", "중구"),
    DONG("14", "동구"),
    SEO("17", "서구"),
    NAM("20", "남구"),
    BUK("23", "북구"),
    SUSEONG("26", "수성구"),
    DALSEO("29", "달서구"),
    DALSEONG("71", "달성군"),
    GUNWI("72", "군위군"),
    OTHER("99", "기타");

    private final String code;
    private final String displayName;

    RegionCode(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<RegionCode> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String trimmed = code.trim();
        return Arrays.stream(values()).filter(region -> region.code.equals(trimmed)).findFirst();
    }
}
