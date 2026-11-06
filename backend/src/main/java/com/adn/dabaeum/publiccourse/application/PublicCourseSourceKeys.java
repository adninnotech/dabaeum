package com.adn.dabaeum.publiccourse.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 공공데이터 강좌의 우리 쪽 고유키를 만든다 (설계서 §3.3).
 *
 * <p>자연키는 원본 값(앞뒤 공백 제거)으로 만들어 정규화 규칙이 바뀌어도 키가 바뀌지 않는다.
 * 한 기관 수집 결과 안에서 자연키가 겹치면 나머지 필드 전체를 직렬화한 문자열 순서로 순번을 붙인다.
 */
public final class PublicCourseSourceKeys {

    static final List<String> NATURAL_KEY_FIELDS = List.of(
        "insttCode", "operInstitutionNm", "lctreNm", "edcStartDay", "edcEndDay", "edcStartTime", "edcPlace");
    private static final String UNIT_SEPARATOR = "\u001F";
    private static final String RECORD_SEPARATOR = "\u001E";

    /** 한 기관의 원본 행 목록에 키를 붙인다. 결과 순서는 입력 순서와 같다. */
    public Assignment assign(List<Map<String, String>> rows) {
        Objects.requireNonNull(rows, "rows");
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            groups.computeIfAbsent(naturalKey(rows.get(index)), key -> new ArrayList<>()).add(index);
        }
        String[] keys = new String[rows.size()];
        int collisionGroups = 0;
        for (Map.Entry<String, List<Integer>> group : groups.entrySet()) {
            List<Integer> members = group.getValue();
            if (members.size() == 1) {
                keys[members.getFirst()] = sha256(group.getKey());
                continue;
            }
            collisionGroups++;
            List<Integer> ordered = members.stream()
                .sorted(Comparator.comparing((Integer index) -> remainder(rows.get(index))))
                .toList();
            for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
                keys[ordered.get(ordinal)] = sha256(group.getKey() + "#" + ordinal);
            }
        }
        List<KeyedRow> keyed = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            keyed.add(new KeyedRow(keys[index], rows.get(index)));
        }
        return new Assignment(keyed, collisionGroups);
    }

    /** 원본 행 전체의 해시. 갱신 여부를 판단하는 데 쓴다. 필드 순서와 무관하다. */
    public String rawHash(Map<String, String> row) {
        return sha256(canonical(row, List.of()));
    }

    private static String naturalKey(Map<String, String> row) {
        return NATURAL_KEY_FIELDS.stream()
            .map(field -> trimmed(row.get(field)))
            .collect(Collectors.joining(UNIT_SEPARATOR));
    }

    private static String remainder(Map<String, String> row) {
        return canonical(row, NATURAL_KEY_FIELDS);
    }

    private static String canonical(Map<String, String> row, List<String> excluded) {
        return new TreeMap<>(row).entrySet().stream()
            .filter(entry -> !excluded.contains(entry.getKey()))
            .map(entry -> entry.getKey() + UNIT_SEPARATOR + (entry.getValue() == null ? "" : entry.getValue()))
            .collect(Collectors.joining(RECORD_SEPARATOR));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.strip();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record KeyedRow(String sourceKey, Map<String, String> row) {

        public KeyedRow {
            Objects.requireNonNull(sourceKey, "sourceKey");
            row = Collections.unmodifiableMap(new LinkedHashMap<>(row));
        }
    }

    /** 키를 붙인 행과 자연키가 겹친 묶음 수. */
    public record Assignment(List<KeyedRow> rows, int collisionGroups) {

        public Assignment {
            rows = List.copyOf(rows);
        }
    }
}
