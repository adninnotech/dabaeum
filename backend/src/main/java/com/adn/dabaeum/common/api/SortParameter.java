package com.adn.dabaeum.common.api;

import java.util.Optional;

/** 목록 API 의 정렬 파라미터 "field,direction" 을 두 조각으로 나눈다. 값 해석은 호출한 쪽이 한다. */
public final class SortParameter {

    private SortParameter() {
    }

    /** 형식이 맞으면 [field, direction]. null·조각 수 불일치·빈 조각이면 비어 있다. */
    public static Optional<String[]> split(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parts);
    }
}
