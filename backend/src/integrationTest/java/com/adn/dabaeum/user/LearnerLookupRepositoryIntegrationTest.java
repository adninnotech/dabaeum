package com.adn.dabaeum.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 대리 수강신청 대상 조회. ACTIVE, 전역 또는 그 기관의 LEARNER 이고 이름·생년월일·휴대전화가 모두 맞아야 한다. */
class LearnerLookupRepositoryIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final LocalDate BIRTH = LocalDate.of(1990, 1, 15);
    private static final String NAME = "조회 학습자";

    @Autowired UserRepository users;

    @Test
    void comparesPhoneDigitsAndRespectsInstitutionScope() {
        UUID institution = insertInstitution();
        UUID other = insertInstitution();
        String digits = phone();
        UUID global = insertUser(hyphenated(digits), "ACTIVE", BIRTH);
        grant(global, "LEARNER", null);
        String scopedDigits = phone();
        UUID scoped = insertUser(scopedDigits, "ACTIVE", BIRTH);
        grant(scoped, "LEARNER", other);

        assertThat(users.findActiveLearnersByPhone(digits, NAME, BIRTH, institution, 2))
            .extracting(User::id).containsExactly(global);
        assertThat(users.findActiveLearnersByPhone(scopedDigits, NAME, BIRTH, institution, 2)).isEmpty();
        assertThat(users.findActiveLearnersByPhone(scopedDigits, NAME, BIRTH, other, 2))
            .extracting(User::id).containsExactly(scoped);
    }

    @Test
    void everyValueMustMatchAndMissingValuesNeverMatch() {
        UUID institution = insertInstitution();
        String digits = phone();
        UUID learner = insertUser(digits, "ACTIVE", BIRTH);
        grant(learner, "LEARNER", null);
        String noBirthDigits = phone();
        grant(insertUser(noBirthDigits, "ACTIVE", null), "LEARNER", null);

        assertThat(users.findActiveLearnersByPhone(digits, "다른 이름", BIRTH, institution, 2)).isEmpty();
        assertThat(users.findActiveLearnersByPhone(digits, NAME, BIRTH.plusDays(1), institution, 2)).isEmpty();
        assertThat(users.findActiveLearnersByPhone(phone(), NAME, BIRTH, institution, 2)).isEmpty();
        assertThat(users.findActiveLearnersByPhone(noBirthDigits, NAME, BIRTH, institution, 2)).isEmpty();
    }

    @Test
    void skipsInactiveUsersAndNonLearners() {
        UUID institution = insertInstitution();
        String suspendedDigits = phone();
        grant(insertUser(suspendedDigits, "SUSPENDED", BIRTH), "LEARNER", null);
        String instructorDigits = phone();
        grant(insertUser(instructorDigits, "ACTIVE", BIRTH), "INSTRUCTOR", institution);

        assertThat(users.findActiveLearnersByPhone(suspendedDigits, NAME, BIRTH, institution, 2)).isEmpty();
        assertThat(users.findActiveLearnersByPhone(instructorDigits, NAME, BIRTH, institution, 2)).isEmpty();
    }

    @Test
    void returnsEveryMatchUpToLimitAndOnePersonOnce() {
        UUID institution = insertInstitution();
        String shared = phone();
        UUID first = insertUser(shared, "ACTIVE", BIRTH);
        grant(first, "LEARNER", null);
        grant(first, "LEARNER", institution);
        UUID second = insertUser(hyphenated(shared), "ACTIVE", BIRTH);
        grant(second, "LEARNER", null);
        UUID third = insertUser(shared, "ACTIVE", BIRTH);
        grant(third, "LEARNER", null);

        assertThat(users.findActiveLearnersByPhone(shared, NAME, BIRTH, institution, 2)).hasSize(2);
        assertThat(users.findActiveLearnersByPhone(shared, NAME, BIRTH, institution, 5)).hasSize(3)
            .extracting(User::id).doesNotHaveDuplicates();
    }

    private static String phone() {
        String random = Long.toString(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
        return "010" + (random + "00000000").substring(0, 8);
    }

    private static String hyphenated(String digits) {
        return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
    }

    private UUID insertUser(String phone, String status, LocalDate birthDate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name, phone, status, birth_date) VALUES (?, ?, ?, ?, ?)",
            id, NAME, phone, status, birthDate);
        return id;
    }

    private void grant(UUID user, String role, UUID institution) {
        jdbcTemplate.update("INSERT INTO tb_user_roles (id, user_id, institution_id, role) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), user, institution, role);
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, "LOOKUP-" + id.toString().substring(0, 8), "조회 테스트 기관");
        return id;
    }
}
