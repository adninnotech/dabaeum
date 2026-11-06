package com.adn.dabaeum.publiccourse.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** 원본 행 정리. PostgreSQL 이 거부하는 문자(NUL, 짝 없는 서로게이트)를 저장 전에 뺀다. */
final class PublicCourseRows {

    private PublicCourseRows() {
    }

    /** 필드 순서를 유지한 새 맵. 호출한 쪽이 고쳐 쓸 수 있다. */
    static Map<String, String> sanitize(Map<String, String> row) {
        Map<String, String> clean = new LinkedHashMap<>();
        row.forEach((key, value) -> clean.put(key, value == null ? null : clean(value)));
        return clean;
    }

    static String clean(String value) {
        StringBuilder out = null;
        int length = value.length();
        for (int index = 0; index < length; index++) {
            char current = value.charAt(index);
            boolean keep;
            if (current == '\u0000') {
                keep = false;
            } else if (Character.isHighSurrogate(current)) {
                keep = index + 1 < length && Character.isLowSurrogate(value.charAt(index + 1));
                if (keep) {
                    if (out != null) {
                        out.append(current).append(value.charAt(index + 1));
                    }
                    index++;
                    continue;
                }
            } else {
                keep = !Character.isLowSurrogate(current);
            }
            if (!keep && out == null) {
                out = new StringBuilder(length).append(value, 0, index);
            } else if (keep && out != null) {
                out.append(current);
            }
        }
        return out == null ? value : out.toString();
    }
}
