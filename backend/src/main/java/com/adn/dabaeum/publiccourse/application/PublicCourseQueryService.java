package com.adn.dabaeum.publiccourse.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.common.api.SortParameter;
import com.adn.dabaeum.publiccourse.domain.PublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseMethod;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository.SortField;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRunRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseTimeOfDay;
import com.adn.dabaeum.publiccourse.domain.RegionCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공공데이터 강좌 공개 조회와 수집 이력 조회 (설계서 §5). 비활성 강좌는 목록에 나오지 않는다. */
@Service
@Profile({"local", "dev"})
public class PublicCourseQueryService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_TARGET_TYPES = 10;
    static final int MAX_TARGET_TYPE_LENGTH = 50;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Map<String, SortField> SORT_FIELDS = Map.of(
        "edcStartDate", SortField.EDC_START_DATE,
        "receiptEndDate", SortField.RECEIPT_END_DATE,
        "lectureName", SortField.LECTURE_NAME,
        "receiptStatus", SortField.RECEIPT_STATUS);
    private static final List<String> WEEKDAYS = List.of("월", "화", "수", "목", "금", "토", "일");
    private static final List<RegionCode> FACET_REGIONS = Arrays.stream(RegionCode.values())
        .filter(region -> region != RegionCode.OTHER)
        .toList();
    private static final List<PublicCourseMethod> FILTERABLE_METHODS = List.of(
        PublicCourseMethod.OFFLINE, PublicCourseMethod.ONLINE, PublicCourseMethod.HYBRID, PublicCourseMethod.VISIT);

    private final PublicCourseRepository courses;
    private final PublicCourseSyncRunRepository runs;
    private final Clock clock;

    public PublicCourseQueryService(PublicCourseRepository courses, PublicCourseSyncRunRepository runs, Clock clock) {
        this.courses = Objects.requireNonNull(courses, "courses");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public Page<PublicCourse> search(ListQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.page(), query.size());
        SortField sortField = sortField(query.sort());
        boolean ascending = ascending(query.sort());
        if (sortField == SortField.RECEIPT_STATUS && !ascending) {
            throw badRequest("sort");
        }
        PublicCourseRepository.Query criteria = criteria(query.filter(), sortField, ascending);
        long total = courses.count(criteria);
        List<PublicCourse> items = total == 0 ? List.of()
            : courses.search(criteria, query.size(), (long) query.page() * query.size());
        return Page.of(items, query.page(), query.size(), total);
    }

    /** 같은 조건의 구·군별 건수. 구·군 조건은 걸지 않고, 대구 9개 구·군을 드롭다운 순서로 0 건까지 채운다. */
    @Transactional(readOnly = true)
    public RegionFacets regionFacets(Filter filter) {
        PublicCourseRepository.Query criteria = criteria(filter, SortField.EDC_START_DATE, true).withoutRegion();
        long total = courses.count(criteria);
        Map<String, Long> counts = total == 0 ? Map.of() : courses.countByRegion(criteria);
        List<RegionCount> regions = FACET_REGIONS.stream()
            .map(region -> new RegionCount(region.code(), counts.getOrDefault(region.code(), 0L)))
            .toList();
        return new RegionFacets(total, regions);
    }

    private PublicCourseRepository.Query criteria(Filter filter, SortField sortField, boolean ascending) {
        Objects.requireNonNull(filter, "filter");
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw badRequest("from");
        }
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        return new PublicCourseRepository.Query(
            KeywordQuery.normalize(filter.q()),
            method(filter.method()),
            targetTypes(filter.targetTypes()),
            region(filter.regionCode()),
            filter.free(),
            filter.from(),
            filter.to(),
            filter.includeEnded() ? null : today,
            filter.receiptOpen() ? today : null,
            operDays(filter.operDays()),
            timeOfDay(filter.timeOfDay()),
            sortField,
            ascending,
            sortField == SortField.RECEIPT_STATUS ? today : null);
    }

    /** 비활성 강좌도 돌려준다(공유 링크 보호, 설계서 §5.2). */
    @Transactional(readOnly = true)
    public PublicCourse detail(UUID id) {
        return courses.findById(Objects.requireNonNull(id, "id"))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.PUBLIC_COURSE_NOT_FOUND,
                "Public course not found"));
    }

    @Transactional(readOnly = true)
    public Page<PublicCourseSyncRun> runs(int page, int size) {
        requirePage(page, size);
        long total = runs.count();
        List<PublicCourseSyncRun> items = total == 0 ? List.of() : runs.findPage(size, (long) page * size);
        return Page.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public PublicCourseSyncRun run(UUID id) {
        return runs.findById(Objects.requireNonNull(id, "id"))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.PUBLIC_COURSE_SYNC_RUN_NOT_FOUND,
                "Public course sync run not found"));
    }

    private static void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("page");
        }
    }

    private static PublicCourseMethod method(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return FILTERABLE_METHODS.stream()
            .filter(method -> method.name().equals(value.strip()))
            .findFirst()
            .orElseThrow(() -> badRequest("method"));
    }

    private static List<String> targetTypes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> cleaned = values.stream()
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        if (cleaned.size() > MAX_TARGET_TYPES
            || cleaned.stream().anyMatch(value -> value.length() > MAX_TARGET_TYPE_LENGTH)) {
            throw badRequest("targetType");
        }
        return cleaned;
    }

    private static List<String> operDays(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> cleaned = values.stream()
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        if (!WEEKDAYS.containsAll(cleaned)) {
            throw badRequest("operDay");
        }
        return cleaned;
    }

    private static PublicCourseTimeOfDay timeOfDay(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(PublicCourseTimeOfDay.values())
            .filter(timeOfDay -> timeOfDay.name().equals(value.strip()))
            .findFirst()
            .orElseThrow(() -> badRequest("timeOfDay"));
    }

    private static String region(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return RegionCode.fromCode(value.strip())
            .filter(region -> region != RegionCode.OTHER)
            .map(RegionCode::code)
            .orElseThrow(() -> badRequest("regionCode"));
    }

    private static SortField sortField(String sort) {
        String[] parts = SortParameter.split(sort).orElseThrow(() -> badRequest("sort"));
        SortField field = SORT_FIELDS.get(parts[0].strip());
        if (field == null) {
            throw badRequest("sort");
        }
        return field;
    }

    private static boolean ascending(String sort) {
        String direction = SortParameter.split(sort).orElseThrow(() -> badRequest("sort"))[1].strip();
        if ("asc".equalsIgnoreCase(direction)) {
            return true;
        }
        if ("desc".equalsIgnoreCase(direction)) {
            return false;
        }
        throw badRequest("sort");
    }

    private static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Invalid request", List.of(detail));
    }

    /** 공개 목록·건수의 조건. 문자열은 컨트롤러가 받은 그대로다. */
    public record Filter(
        String q, String method, List<String> targetTypes, String regionCode, Boolean free, LocalDate from,
        LocalDate to, boolean includeEnded, boolean receiptOpen, List<String> operDays, String timeOfDay) {
    }

    /** 공개 목록 요청 값. */
    public record ListQuery(Filter filter, int page, int size, String sort) {
    }

    public record RegionCount(String regionCode, long count) {
    }

    /** {@code total} 은 구·군을 모르는 강좌까지 센 전체 건수다. */
    public record RegionFacets(long total, List<RegionCount> regions) {

        public RegionFacets {
            regions = List.copyOf(regions);
        }
    }

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

        public Page {
            items = List.copyOf(items);
        }

        static <T> Page<T> of(List<T> items, int page, int size, long totalElements) {
            int totalPages = (int) ((totalElements + size - 1) / size);
            return new Page<>(items, page, size, totalElements, totalPages);
        }
    }
}
