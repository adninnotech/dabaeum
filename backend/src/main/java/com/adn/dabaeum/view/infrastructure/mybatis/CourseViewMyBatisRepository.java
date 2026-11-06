package com.adn.dabaeum.view.infrastructure.mybatis;

import com.adn.dabaeum.view.domain.CourseViewRepository;
import com.adn.dabaeum.view.domain.ViewSaltRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseViewMyBatisRepository implements CourseViewRepository, ViewSaltRepository {

    private static final int SALT_BYTES = 32;

    private final CourseViewMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public CourseViewMyBatisRepository(CourseViewMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isPublicCourse(UUID courseId) {
        return mapper.isPublicCourse(courseId);
    }

    @Override
    public void record(UUID courseId, String viewerKey, LocalDate viewDate, UUID userId, Instant at) {
        mapper.insertIfAbsent(courseId, viewerKey, viewDate, userId, at);
    }

    @Override
    public byte[] saltFor(LocalDate date) {
        byte[] candidate = new byte[SALT_BYTES];
        random.nextBytes(candidate);
        // 먼저 넣은 서버의 salt 가 이긴다. 그 뒤 모두 같은 행을 읽는다.
        mapper.insertSaltIfAbsent(date, candidate);
        return mapper.selectSalt(date).salt();
    }

    @Override
    public int deleteViewsBefore(LocalDate date) {
        return mapper.deleteViewsBefore(date);
    }

    @Override
    public int deleteSaltsBefore(LocalDate date) {
        return mapper.deleteSaltsBefore(date);
    }
}
