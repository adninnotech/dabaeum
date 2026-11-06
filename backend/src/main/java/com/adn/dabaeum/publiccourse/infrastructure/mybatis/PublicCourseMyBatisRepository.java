package com.adn.dabaeum.publiccourse.infrastructure.mybatis;

import com.adn.dabaeum.publiccourse.domain.NormalizedPublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseMethod;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class PublicCourseMyBatisRepository implements PublicCourseRepository {

    /** 배열 컬럼을 문자열로 주고받을 때의 구분자. SQL 의 chr(31) 과 같다. */
    static final String ARRAY_SEPARATOR = "\u001F";

    /** touch 의 IN 목록 크기. pgjdbc 파라미터 한도(65,535) 아래로 나눠 보낸다. */
    static final int TOUCH_CHUNK = 5_000;

    private final PublicCourseMapper mapper;

    public PublicCourseMyBatisRepository(PublicCourseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Stored> findStoredByInsttCode(String insttCode) {
        return mapper.selectStoredByInsttCode(insttCode).stream()
            .collect(Collectors.toUnmodifiableMap(PublicCourseMapper.StoredRow::sourceKey,
                row -> new Stored(row.id(), row.rawHash(), row.active())));
    }

    @Override
    public void insert(PublicCourse course) {
        mapper.insert(toRow(course));
    }

    @Override
    public void replace(PublicCourse course) {
        mapper.replace(toRow(course));
    }

    @Override
    public int touch(List<String> sourceKeys, Instant seenAt) {
        int touched = 0;
        for (int from = 0; from < sourceKeys.size(); from += TOUCH_CHUNK) {
            touched += mapper.touch(sourceKeys.subList(from, Math.min(sourceKeys.size(), from + TOUCH_CHUNK)), seenAt);
        }
        return touched;
    }

    @Override
    public int deactivateUnseen(String insttCode, Instant seenAt) {
        return mapper.deactivateUnseen(insttCode, seenAt);
    }

    @Override
    public Optional<PublicCourse> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(PublicCourseMyBatisRepository::toDomain);
    }

    @Override
    public List<PublicCourse> search(Query query, int limit, long offset) {
        return mapper.search(params(query), limit, offset).stream()
            .map(PublicCourseMyBatisRepository::toDomain)
            .toList();
    }

    @Override
    public long count(Query query) {
        return mapper.count(params(query));
    }

    @Override
    public Map<String, Long> countByRegion(Query query) {
        return mapper.countByRegion(params(query)).stream()
            .collect(Collectors.toUnmodifiableMap(PublicCourseMapper.RegionCountRow::regionCode,
                PublicCourseMapper.RegionCountRow::count));
    }

    private static PublicCourseMapper.QueryParams params(Query query) {
        String sortColumn = switch (query.sortField()) {
            case EDC_START_DATE -> "edc_start_date";
            case RECEIPT_END_DATE -> "receipt_end_date";
            case LECTURE_NAME -> "lecture_name";
            case RECEIPT_STATUS -> "receipt_end_date";
        };
        return new PublicCourseMapper.QueryParams(
            query.keyword(),
            query.method() == null ? null : query.method().name(),
            query.targetTypes().isEmpty() ? null : join(query.targetTypes()),
            query.regionCode(),
            query.free(),
            query.from(),
            query.to(),
            query.endedFrom(),
            query.receiptOpenOn(),
            query.operDays().isEmpty() ? null : join(query.operDays()),
            query.timeOfDay() == null ? null : query.timeOfDay().from(),
            query.timeOfDay() == null ? null : query.timeOfDay().before(),
            sortColumn,
            query.ascending(),
            query.receiptStatusOn());
    }

    private static PublicCourseMapper.CourseRow toRow(PublicCourse course) {
        NormalizedPublicCourse value = course.course();
        return new PublicCourseMapper.CourseRow(
            course.id(), course.sourceKey(), course.rawHash(),
            value.insttCode(), value.insttName(), value.lectureName(), value.instructorName(),
            value.lectureContent(), value.place(), value.roadAddress(), value.operInstitutionName(),
            value.operPhone(), value.homepageUrl(), value.edcStartDate(), value.edcEndDate(),
            value.startTime(), value.endTime(), join(value.operDays()), join(value.targetTypes()),
            value.method() == null ? null : value.method().name(), value.regionCode(), value.capacity(),
            value.costAmount(), value.free(), value.receiptStartDate(), value.receiptEndDate(),
            value.receiptMethod(), value.selectionMethod(), value.trainingSubsidy(), value.creditBank(),
            value.learningAccount(), value.referenceDate(), course.rawJson(), course.active(),
            course.firstSeenAt(), course.lastSeenAt(), course.deactivatedAt());
    }

    private static PublicCourse toDomain(PublicCourseMapper.CourseRow row) {
        NormalizedPublicCourse value = new NormalizedPublicCourse(
            row.insttCode(), row.insttName(), row.lectureName(), row.instructorName(), row.lectureContent(),
            row.place(), row.roadAddress(), row.operInstitutionName(), row.operPhone(), row.homepageUrl(),
            row.edcStartDate(), row.edcEndDate(), row.startTime(), row.endTime(), split(row.operDays()),
            split(row.targetTypes()), row.method() == null ? null : PublicCourseMethod.valueOf(row.method()),
            row.regionCode(), row.capacity(), row.costAmount(), row.free(), row.receiptStartDate(),
            row.receiptEndDate(), row.receiptMethod(), row.selectionMethod(), row.trainingSubsidy(),
            row.creditBank(), row.learningAccount(), row.referenceDate());
        return new PublicCourse(row.id(), row.sourceKey(), row.rawHash(), value, row.rawJson(), row.active(),
            row.firstSeenAt(), row.lastSeenAt(), row.deactivatedAt());
    }

    private static String join(List<String> values) {
        return String.join(ARRAY_SEPARATOR, values);
    }

    private static List<String> split(String joined) {
        if (joined == null || joined.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(joined.split(ARRAY_SEPARATOR, -1));
    }
}
