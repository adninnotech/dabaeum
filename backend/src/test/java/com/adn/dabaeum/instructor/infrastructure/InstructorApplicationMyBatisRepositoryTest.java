package com.adn.dabaeum.instructor.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.instructor.domain.InstructorApplication;
import com.adn.dabaeum.instructor.domain.InstructorApplicationPageCriteria;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import com.adn.dabaeum.instructor.infrastructure.mybatis.InstructorApplicationMapper;
import com.adn.dabaeum.instructor.infrastructure.mybatis.InstructorApplicationMyBatisRepository;
import com.adn.dabaeum.instructor.infrastructure.mybatis.InstructorApplicationRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstructorApplicationMyBatisRepositoryTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSTITUTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Mock InstructorApplicationMapper mapper;
    private InstructorApplicationMyBatisRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InstructorApplicationMyBatisRepository(mapper);
    }

    @Test
    void savesFindsLocksAndPagesDomainApplications() {
        InstructorApplication application = pending();
        InstructorApplicationRow row = row();
        InstructorApplicationPageCriteria criteria = new InstructorApplicationPageCriteria(
            USER_ID, null, InstructorApplicationStatus.PENDING, 0, 20,
            "APPLIED_AT", "DESC"
        );
        when(mapper.selectById(ID)).thenReturn(row);
        when(mapper.selectByIdForUpdate(ID)).thenReturn(row);
        when(mapper.selectPending(USER_ID, INSTITUTION_ID)).thenReturn(row);
        when(mapper.selectPage(criteria)).thenReturn(List.of(row));
        when(mapper.count(criteria)).thenReturn(1L);

        repository.save(application);

        verify(mapper).insert(row);
        assertThat(repository.findById(ID)).contains(application);
        assertThat(repository.findByIdForUpdate(ID)).contains(application);
        assertThat(repository.findPending(USER_ID, INSTITUTION_ID)).contains(application);
        assertThat(repository.findPage(criteria)).containsExactly(application);
        assertThat(repository.count(criteria)).isEqualTo(1);
    }

    @Test
    void updatesReviewOnlyFromExpectedPendingStatus() {
        InstructorApplication approved = new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.APPROVED,
            "경력", null, UUID.randomUUID(), NOW, NOW, NOW, NOW
        );
        when(mapper.updateReview(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(InstructorApplicationStatus.PENDING))).thenReturn(1);

        assertThat(repository.updateReview(
            approved, InstructorApplicationStatus.PENDING
        )).isTrue();
    }

    private InstructorApplication pending() {
        return new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.PENDING,
            "경력", null, null, NOW, null, NOW, NOW
        );
    }

    private InstructorApplicationRow row() {
        return new InstructorApplicationRow(
            ID, USER_ID, INSTITUTION_ID, "PENDING", "경력", null,
            null, NOW, null, NOW, NOW
        );
    }
}
