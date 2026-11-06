package com.adn.dabaeum.publiccourse.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.publiccourse.domain.NormalizedPublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseMethod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 설계서 §3.2 — 원본 행 → 정규화 컬럼. 해석 실패는 null + 실패 건수, 필수값 없으면 건너뜀. */
class PublicCourseNormalizerTest {

    private final PublicCourseNormalizer normalizer = new PublicCourseNormalizer();

    @Test
    void normalizesRealSuseongRow() {
        PublicCourseNormalizer.Result result = normalizer.normalize(suseongRow());

        assertThat(result.skipped()).isFalse();
        assertThat(result.parseFailures()).isZero();
        NormalizedPublicCourse course = result.course();
        assertThat(course.insttCode()).isEqualTo("3460000");
        assertThat(course.lectureName()).isEqualTo("필라테스 평일");
        assertThat(course.edcStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(course.edcEndDate()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(course.startTime()).isEqualTo(LocalTime.of(14, 10));
        assertThat(course.endTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(course.operDays()).containsExactly("월", "수");
        assertThat(course.targetTypes()).containsExactly("지역주민");
        assertThat(course.method()).isEqualTo(PublicCourseMethod.OFFLINE);
        assertThat(course.capacity()).isEqualTo(25);
        assertThat(course.costAmount()).isEqualTo(45000);
        assertThat(course.free()).isFalse();
        assertThat(course.regionCode()).isEqualTo("26");
        assertThat(course.homepageUrl()).isEqualTo("https://lll.suseong.kr");
        assertThat(course.trainingSubsidy()).isFalse();
        assertThat(course.referenceDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void blankOptionalValuesBecomeNullWithoutCountingAsFailures() {
        Map<String, String> row = suseongRow();
        row.put("edcStartTime", "");
        row.put("edcColseTime", "");
        row.put("operDay", "");
        row.put("slctnMthType", "");
        row.put("homepageUrl", "");
        row.put("oadtCtLctreYn", " ");

        PublicCourseNormalizer.Result result = normalizer.normalize(row);

        assertThat(result.parseFailures()).isZero();
        NormalizedPublicCourse course = result.course();
        assertThat(course.startTime()).isNull();
        assertThat(course.endTime()).isNull();
        assertThat(course.operDays()).isEmpty();
        assertThat(course.selectionMethod()).isNull();
        assertThat(course.homepageUrl()).isNull();
        assertThat(course.trainingSubsidy()).isNull();
    }

    @Test
    void unparseableValuesBecomeNullAndAreCounted() {
        Map<String, String> row = suseongRow();
        row.put("edcStartTime", "오후 2시");
        row.put("lctreCost", "3만원");
        row.put("psncpa", "-1");
        row.put("homepageUrl", "javascript:alert(1)");

        PublicCourseNormalizer.Result result = normalizer.normalize(row);

        assertThat(result.skipped()).isFalse();
        assertThat(result.parseFailures()).isEqualTo(4);
        NormalizedPublicCourse course = result.course();
        assertThat(course.startTime()).isNull();
        assertThat(course.costAmount()).isNull();
        assertThat(course.free()).isNull();
        assertThat(course.capacity()).isNull();
        assertThat(course.homepageUrl()).isNull();
    }

    @Test
    void zeroCostIsFree() {
        Map<String, String> row = suseongRow();
        row.put("lctreCost", "0");

        assertThat(normalizer.normalize(row).course().free()).isTrue();
    }

    @Test
    void compactDateFormatIsAccepted() {
        Map<String, String> row = suseongRow();
        row.put("edcStartDay", "20260901");

        assertThat(normalizer.normalize(row).course().edcStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void skipsRowWithoutLectureName() {
        Map<String, String> row = suseongRow();
        row.put("lctreNm", "  ");

        assertThat(normalizer.normalize(row).skipped()).isTrue();
    }

    @Test
    void skipsRowWhenBothEducationDatesAreUnreadable() {
        Map<String, String> row = suseongRow();
        row.put("edcStartDay", "미정");
        row.put("edcEndDay", "");

        assertThat(normalizer.normalize(row).skipped()).isTrue();
    }

    @Test
    void keepsRowWhenOnlyOneEducationDateIsReadable() {
        Map<String, String> row = suseongRow();
        row.put("edcEndDay", "미정");

        PublicCourseNormalizer.Result result = normalizer.normalize(row);

        assertThat(result.skipped()).isFalse();
        assertThat(result.course().edcEndDate()).isNull();
        assertThat(result.parseFailures()).isEqualTo(1);
    }

    @Test
    void splitsMultiValueTargetTypes() {
        Map<String, String> row = suseongRow();
        row.put("edcTrgetType", "청년/직장인+ 성인 +어르신+");

        assertThat(normalizer.normalize(row).course().targetTypes())
            .containsExactly("청년/직장인", "성인", "어르신");
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "월+수;월,수",
        "화|목;화,목",
        "월, 수/금;월,수,금",
        "토요일;토",
        "평일;''"
    })
    void normalizesOperDays(String raw, String expected) {
        Map<String, String> row = suseongRow();
        row.put("operDay", raw);

        List<String> days = normalizer.normalize(row).course().operDays();

        assertThat(String.join(",", days)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "오프라인,OFFLINE",
        "온라인,ONLINE",
        "혼합,HYBRID",
        "방문교육,VISIT",
        "통신,OTHER"
    })
    void mapsEducationMethod(String raw, PublicCourseMethod expected) {
        Map<String, String> row = suseongRow();
        row.put("edcMthType", raw);

        assertThat(normalizer.normalize(row).course().method()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "대구광역시 달서구 학산로 1;29",
        "대구광역시 서구 국채보상로 257;17",
        "대구광역시 군위군 군위읍 동서길 1;72",
        "대구광역시 달성군 논공읍 1;71",
        "부산광역시 서구 구덕로 1;",
        "경상남도 진주시 내동면 삼계로 430;"
    })
    void derivesDaeguRegionFromRoadAddress(String address, String expected) {
        Map<String, String> row = suseongRow();
        row.put("edcRdnmadr", address);

        assertThat(normalizer.normalize(row).course().regionCode()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "https://lll.suseong.kr/main;https://lll.suseong.kr/main",
        "HTTP://www.gunwi.go.kr;HTTP://www.gunwi.go.kr",
        "library.daegu.go.kr/dalseonglib;https://library.daegu.go.kr/dalseonglib",
        "www.dong.daegu.kr/80edu/;https://www.dong.daegu.kr/80edu/"
    })
    void normalizesHomepageUrl(String raw, String expected) {
        Map<String, String> row = suseongRow();
        row.put("homepageUrl", raw);

        assertThat(normalizer.normalize(row).course().homepageUrl()).isEqualTo(expected);
    }

    @Test
    void overlongTextBecomesNullAndIsCounted() {
        Map<String, String> row = suseongRow();
        row.put("edcPlace", "가".repeat(PublicCourseNormalizer.MAX_TEXT_LENGTH + 1));

        PublicCourseNormalizer.Result result = normalizer.normalize(row);

        assertThat(result.course().place()).isNull();
        assertThat(result.parseFailures()).isEqualTo(1);
    }

    @Test
    void homepageLengthIsCheckedAfterAddingScheme() {
        Map<String, String> row = suseongRow();
        row.put("homepageUrl", "a".repeat(PublicCourseNormalizer.MAX_TEXT_LENGTH - 10) + ".kr");

        PublicCourseNormalizer.Result result = normalizer.normalize(row);

        assertThat(result.course().homepageUrl()).isNull();
        assertThat(result.parseFailures()).isEqualTo(1);
    }

    static Map<String, String> suseongRow() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("lctreNm", "필라테스 평일");
        row.put("instrctrNm", "김경희");
        row.put("edcStartDay", "2026-09-01");
        row.put("edcEndDay", "2026-11-23");
        row.put("edcStartTime", "14:10");
        row.put("edcColseTime", "15:00");
        row.put("lctreCo", "소도구를 이용한 신체의 유연성 및 근력강화+자세교정");
        row.put("edcTrgetType", "지역주민");
        row.put("edcMthType", "오프라인");
        row.put("operDay", "월|수");
        row.put("edcPlace", "수성동평생학습센터");
        row.put("psncpa", "25");
        row.put("lctreCost", "45000");
        row.put("edcRdnmadr", "대구광역시 수성구 명덕로 73길 7(수성동 1가)");
        row.put("operInstitutionNm", "대구광역시 수성구청");
        row.put("operPhoneNumber", "053-666-2693");
        row.put("rceptStartDate", "2026-08-04");
        row.put("rceptEndDate", "2026-08-10");
        row.put("rceptMthType", "인터넷");
        row.put("slctnMthType", "선착순");
        row.put("homepageUrl", "lll.suseong.kr");
        row.put("oadtCtLctreYn", "N");
        row.put("pntBankAckestYn", "N");
        row.put("lrnAcnutAckestYn", "N");
        row.put("referenceDate", "2026-08-10");
        row.put("insttCode", "3460000");
        row.put("insttNm", "대구광역시 수성구");
        return row;
    }
}
