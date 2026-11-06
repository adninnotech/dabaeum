package com.adn.dabaeum.publiccourse.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 설계서 §3.3 — 원본 값 자연키 해시, 겹치면 나머지 필드 순서로 순번. */
class PublicCourseSourceKeysTest {

    private final PublicCourseSourceKeys keys = new PublicCourseSourceKeys();

    @Test
    void sameNaturalKeyGivesSameHashRegardlessOfOtherFields() {
        Map<String, String> a = PublicCourseNormalizerTest.suseongRow();
        Map<String, String> b = PublicCourseNormalizerTest.suseongRow();
        b.put("psncpa", "99");
        b.put("lctreCost", "0");

        String first = keys.assign(List.of(a)).rows().getFirst().sourceKey();
        String second = keys.assign(List.of(b)).rows().getFirst().sourceKey();

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(second);
    }

    @Test
    void surroundingWhitespaceInKeyFieldsIsIgnored() {
        Map<String, String> a = PublicCourseNormalizerTest.suseongRow();
        Map<String, String> b = PublicCourseNormalizerTest.suseongRow();
        b.put("lctreNm", "  필라테스 평일 ");

        assertThat(keys.assign(List.of(b)).rows().getFirst().sourceKey())
            .isEqualTo(keys.assign(List.of(a)).rows().getFirst().sourceKey());
    }

    @Test
    void differentKeyFieldGivesDifferentHash() {
        Map<String, String> a = PublicCourseNormalizerTest.suseongRow();
        Map<String, String> b = PublicCourseNormalizerTest.suseongRow();
        b.put("edcPlace", "범어도서관");

        PublicCourseSourceKeys.Assignment result = keys.assign(List.of(a, b));

        assertThat(result.rows().get(0).sourceKey()).isNotEqualTo(result.rows().get(1).sourceKey());
        assertThat(result.collisionGroups()).isZero();
    }

    @Test
    void collidingRowsGetStableOrdinalsIndependentOfInputOrder() {
        Map<String, String> big = PublicCourseNormalizerTest.suseongRow();
        big.put("psncpa", "30");
        Map<String, String> small = PublicCourseNormalizerTest.suseongRow();
        small.put("psncpa", "10");

        PublicCourseSourceKeys.Assignment forward = keys.assign(List.of(big, small));
        PublicCourseSourceKeys.Assignment backward = keys.assign(List.of(small, big));

        assertThat(forward.collisionGroups()).isEqualTo(1);
        assertThat(keyOf(forward, "30")).isEqualTo(keyOf(backward, "30"));
        assertThat(keyOf(forward, "10")).isEqualTo(keyOf(backward, "10"));
        assertThat(keyOf(forward, "30")).isNotEqualTo(keyOf(forward, "10"));
    }

    @Test
    void nonCollidingRowKeepsKeyWhenAnotherRowCollides() {
        Map<String, String> alone = PublicCourseNormalizerTest.suseongRow();
        alone.put("lctreNm", "요가");
        String before = keys.assign(List.of(alone)).rows().getFirst().sourceKey();

        Map<String, String> a = PublicCourseNormalizerTest.suseongRow();
        Map<String, String> b = PublicCourseNormalizerTest.suseongRow();
        b.put("psncpa", "10");
        PublicCourseSourceKeys.Assignment result = keys.assign(List.of(a, alone, b));

        assertThat(result.rows()).extracting(PublicCourseSourceKeys.KeyedRow::sourceKey).contains(before);
    }

    @Test
    void threeWayCollisionCountsAsOneGroupWithDistinctKeys() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String capacity : List.of("10", "20", "30")) {
            Map<String, String> row = PublicCourseNormalizerTest.suseongRow();
            row.put("psncpa", capacity);
            rows.add(row);
        }

        PublicCourseSourceKeys.Assignment result = keys.assign(rows);

        assertThat(result.collisionGroups()).isEqualTo(1);
        assertThat(result.rows()).extracting(PublicCourseSourceKeys.KeyedRow::sourceKey).doesNotHaveDuplicates();
    }

    @Test
    void rawHashChangesWhenAnyFieldChanges() {
        Map<String, String> a = PublicCourseNormalizerTest.suseongRow();
        Map<String, String> b = new LinkedHashMap<>(a);
        b.put("operPhoneNumber", "053-000-0000");

        assertThat(keys.rawHash(a)).hasSize(64).isNotEqualTo(keys.rawHash(b));
        assertThat(keys.rawHash(a)).isEqualTo(keys.rawHash(new LinkedHashMap<>(a)));
    }

    private static String keyOf(PublicCourseSourceKeys.Assignment assignment, String capacity) {
        return assignment.rows().stream()
            .filter(row -> capacity.equals(row.row().get("psncpa")))
            .findFirst().orElseThrow().sourceKey();
    }
}
