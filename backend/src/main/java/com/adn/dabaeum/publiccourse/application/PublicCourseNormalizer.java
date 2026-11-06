package com.adn.dabaeum.publiccourse.application;

import com.adn.dabaeum.publiccourse.domain.NormalizedPublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseMethod;
import com.adn.dabaeum.publiccourse.domain.RegionCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 공공데이터 강좌 원본 행을 정규화한다 (설계서 §3.2).
 *
 * <p>빈 값은 실패가 아니라 null 이다. 값이 있는데 해석할 수 없으면 null 로 두고 실패로 센다.
 * 강좌명이 비었거나 교육 시작·종료일을 둘 다 해석할 수 없으면 행을 건너뛴다.
 */
public final class PublicCourseNormalizer {

    /** 강좌내용을 뺀 문자열 컬럼의 상한. 넘으면 자르지 않고 null 로 둔다. */
    public static final int MAX_TEXT_LENGTH = 500;
    static final int MAX_CONTENT_LENGTH = 10_000;

    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("\\d{1,9}");
    private static final Pattern OPER_DAY_SEPARATOR = Pattern.compile("[+|,/\\s]+");
    private static final Pattern WEEKDAY = Pattern.compile("^[월화수목금토일](요일)?$");
    private static final Pattern URL_WITH_SCHEME = Pattern.compile("^(?i)https?://\\S+$");
    private static final Pattern HOST_LIKE = Pattern.compile("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(/\\S*)?$");
    private static final Map<String, PublicCourseMethod> METHODS = Map.of(
        "오프라인", PublicCourseMethod.OFFLINE,
        "온라인", PublicCourseMethod.ONLINE,
        "혼합", PublicCourseMethod.HYBRID,
        "방문교육", PublicCourseMethod.VISIT);

    public Result normalize(Map<String, String> row) {
        Objects.requireNonNull(row, "row");
        Failures failures = new Failures();
        String lectureName = text(row, "lctreNm", MAX_TEXT_LENGTH, failures);
        LocalDate start = date(row, "edcStartDay", failures);
        LocalDate end = date(row, "edcEndDay", failures);
        if (lectureName == null || (start == null && end == null)) {
            return Result.skip();
        }
        String costText = trimmed(row.get("lctreCost"));
        Integer cost = integer(costText, failures);
        NormalizedPublicCourse course = new NormalizedPublicCourse(
            text(row, "insttCode", MAX_TEXT_LENGTH, failures),
            text(row, "insttNm", MAX_TEXT_LENGTH, failures),
            lectureName,
            text(row, "instrctrNm", MAX_TEXT_LENGTH, failures),
            text(row, "lctreCo", MAX_CONTENT_LENGTH, failures),
            text(row, "edcPlace", MAX_TEXT_LENGTH, failures),
            text(row, "edcRdnmadr", MAX_TEXT_LENGTH, failures),
            text(row, "operInstitutionNm", MAX_TEXT_LENGTH, failures),
            text(row, "operPhoneNumber", MAX_TEXT_LENGTH, failures),
            homepage(trimmed(row.get("homepageUrl")), failures),
            start,
            end,
            time(row, "edcStartTime", failures),
            time(row, "edcColseTime", failures),
            operDays(trimmed(row.get("operDay"))),
            targetTypes(trimmed(row.get("edcTrgetType"))),
            method(trimmed(row.get("edcMthType"))),
            region(trimmed(row.get("edcRdnmadr"))).orElse(null),
            integer(trimmed(row.get("psncpa")), failures),
            cost,
            cost == null ? null : cost == 0,
            date(row, "rceptStartDate", failures),
            date(row, "rceptEndDate", failures),
            text(row, "rceptMthType", MAX_TEXT_LENGTH, failures),
            text(row, "slctnMthType", MAX_TEXT_LENGTH, failures),
            yesNo(trimmed(row.get("oadtCtLctreYn"))),
            yesNo(trimmed(row.get("pntBankAckestYn"))),
            yesNo(trimmed(row.get("lrnAcnutAckestYn"))),
            date(row, "referenceDate", failures));
        return Result.of(course, failures.count);
    }

    private static String text(Map<String, String> row, String field, int maxLength, Failures failures) {
        String value = trimmed(row.get(field));
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            failures.count++;
            return null;
        }
        return value;
    }

    private static LocalDate date(Map<String, String> row, String field, Failures failures) {
        String value = trimmed(row.get(field));
        if (value == null) {
            return null;
        }
        try {
            return value.length() == 8 ? LocalDate.parse(value, COMPACT_DATE) : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            failures.count++;
            return null;
        }
    }

    private static LocalTime time(Map<String, String> row, String field, Failures failures) {
        String value = trimmed(row.get(field));
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value, TIME);
        } catch (DateTimeParseException exception) {
            failures.count++;
            return null;
        }
    }

    private static Integer integer(String value, Failures failures) {
        if (value == null) {
            return null;
        }
        if (!NON_NEGATIVE_INTEGER.matcher(value).matches()) {
            failures.count++;
            return null;
        }
        return Integer.valueOf(value);
    }

    private static String homepage(String value, Failures failures) {
        if (value == null) {
            return null;
        }
        String url;
        if (URL_WITH_SCHEME.matcher(value).matches()) {
            url = value;
        } else if (HOST_LIKE.matcher(value).matches()) {
            url = "https://" + value;
        } else {
            failures.count++;
            return null;
        }
        // 스킴을 붙인 뒤의 길이로 본다(컬럼 VARCHAR(500)).
        if (url.length() > MAX_TEXT_LENGTH) {
            failures.count++;
            return null;
        }
        return url;
    }

    private static List<String> operDays(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(OPER_DAY_SEPARATOR.split(value))
            .filter(token -> WEEKDAY.matcher(token).matches())
            .map(token -> token.substring(0, 1))
            .distinct()
            .toList();
    }

    private static List<String> targetTypes(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split("\\+"))
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .distinct()
            .toList();
    }

    private static PublicCourseMethod method(String value) {
        if (value == null) {
            return null;
        }
        return METHODS.getOrDefault(value, PublicCourseMethod.OTHER);
    }

    /** 대구 주소의 구·군 토큰만 본다. 다른 시·도의 같은 이름 구(예: 부산 서구)는 무시한다. */
    private static Optional<String> region(String address) {
        if (address == null || !address.startsWith("대구")) {
            return Optional.empty();
        }
        List<String> tokens = Arrays.asList(address.split("\\s+"));
        return Arrays.stream(RegionCode.values())
            .filter(region -> region != RegionCode.OTHER)
            .filter(region -> tokens.contains(region.displayName()))
            .map(RegionCode::code)
            .findFirst();
    }

    private static Boolean yesNo(String value) {
        if ("Y".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("N".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String result = value.strip();
        return result.isEmpty() ? null : result;
    }

    /** 한 행을 해석하는 동안만 쓰는 실패 계수. */
    private static final class Failures {
        private int count;
    }

    /** 정규화 결과. 건너뛴 행이면 {@code course} 가 null 이다. */
    public record Result(NormalizedPublicCourse course, int parseFailures) {

        static Result of(NormalizedPublicCourse course, int parseFailures) {
            return new Result(Objects.requireNonNull(course, "course"), parseFailures);
        }

        static Result skip() {
            return new Result(null, 0);
        }

        public boolean skipped() {
            return course == null;
        }
    }
}
