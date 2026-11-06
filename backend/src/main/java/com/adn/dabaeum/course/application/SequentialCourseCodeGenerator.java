package com.adn.dabaeum.course.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** C-YYYYMM-XXXXXX. 사람이 받아 적는 코드라 1·0 과 헷갈리는 I·O 를 뺐다. */
@Component
public class SequentialCourseCodeGenerator implements CourseCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int SUFFIX_LENGTH = 6;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MONTH =
        DateTimeFormatter.ofPattern("yyyyMM");

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SequentialCourseCodeGenerator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String generate(UUID institutionId) {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "C-" + MONTH.format(clock.instant().atZone(SEOUL)) + "-" + suffix;
    }
}
