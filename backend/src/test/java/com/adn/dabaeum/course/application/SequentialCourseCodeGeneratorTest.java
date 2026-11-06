package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SequentialCourseCodeGeneratorTest {

    private final Clock clock = Clock.fixed(
        Instant.parse("2026-09-16T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 연월과_임의문자열로_코드를_만든다() {
        CourseCodeGenerator generator = new SequentialCourseCodeGenerator(clock);

        String code = generator.generate(UUID.randomUUID());

        assertThat(code).matches("C-202609-[0-9A-HJ-NP-Z]{6}");
    }

    @Test
    void 코드는_50자를_넘지_않는다() {
        CourseCodeGenerator generator = new SequentialCourseCodeGenerator(clock);

        assertThat(generator.generate(UUID.randomUUID()).length())
            .isLessThanOrEqualTo(50);
    }

    @Test
    void 만번_뽑아도_충돌하지_않는다() {
        CourseCodeGenerator generator = new SequentialCourseCodeGenerator(clock);
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            codes.add(generator.generate(UUID.randomUUID()));
        }

        // 34^6 = 약 15억 조합에서 1만 번을 뽑으면 생일 문제로 약 3.2% 확률로 중복이 하나 난다.
        // 정확히 1만 개를 요구하면 테스트가 무작위로 빨개지므로, 생성기가 퇴화했는지만 본다
        // (알파벳이나 길이가 줄면 중복이 수백~수천 개로 폭증해 이 하한에 반드시 걸린다).
        assertThat(codes).hasSizeGreaterThanOrEqualTo(9_990);
    }
}
