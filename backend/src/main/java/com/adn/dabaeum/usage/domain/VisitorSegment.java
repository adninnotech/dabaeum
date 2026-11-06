package com.adn.dabaeum.usage.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.Set;

/**
 * 기록 순간의 회원 여부·성별·연령대 (docs/specs/usage-statistics.md §3.2). user_id 대신 이것만 남긴다.
 * 연령대는 수강 통계(StatisticsMapper.xml ageBandAtApplication)와 같은 규칙이다.
 */
public record VisitorSegment(String memberType, String gender, String ageBand) {

    public static final String UNKNOWN = "UNKNOWN";
    public static final VisitorSegment GUEST = new VisitorSegment("GUEST", UNKNOWN, UNKNOWN);

    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNDISCLOSED");
    private static final int YOUNGEST_BAND = 10;
    private static final int OLDEST_BAND = 70;

    public VisitorSegment {
        Objects.requireNonNull(memberType, "memberType");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(ageBand, "ageBand");
    }

    public static VisitorSegment member(String gender, LocalDate birthDate, LocalDate on) {
        return new VisitorSegment("MEMBER", gender != null && GENDERS.contains(gender) ? gender : UNKNOWN, ageBand(birthDate, on));
    }

    static String ageBand(LocalDate birthDate, LocalDate on) {
        if (birthDate == null) {
            return UNKNOWN;
        }
        int years = Period.between(birthDate, on).getYears();
        return Integer.toString(Math.min(Math.max(years / 10 * 10, YOUNGEST_BAND), OLDEST_BAND));
    }
}
