package com.adn.dabaeum.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.AgreementType;
import com.adn.dabaeum.user.domain.UserAgreement;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserInterestRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** docs/specs/interest-based-member-profile.md §4. 테스트 트랜잭션으로 롤백한다. */
class UserInterestRepositoryIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

    @Autowired UserInterestRepository interests;
    @Autowired UserAgreementRepository agreements;

    @Test
    void replaceSwapsCodesAndEmptyClearsThem() {
        UUID user = insertUser();

        interests.replace(user, List.of("TSIX_050000", "TSIX_040000"), NOW);
        assertThat(interests.findCodes(user)).containsExactly("TSIX_040000", "TSIX_050000");

        interests.replace(user, List.of("TSIX_030000"), NOW);
        assertThat(interests.findCodes(user)).containsExactly("TSIX_030000");

        interests.replace(user, List.of(), NOW);
        assertThat(interests.findCodes(user)).isEmpty();
    }

    @Test
    void unknownClassificationIsRejectedByTheDatabase() {
        UUID user = insertUser();

        assertThatThrownBy(() -> interests.replace(user, List.of("TSIX_999999"), NOW))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void interestConsentIsAcceptedByTheAgreementTable() {
        UUID user = insertUser();

        agreements.saveAll(List.of(new UserAgreement(UUID.randomUUID(), user, AgreementType.INTEREST, true, NOW)));

        assertThat(agreements.findByUserId(user))
            .extracting(UserAgreement::agreementType).contains(AgreementType.INTEREST);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name, status) VALUES (?, '관심분야 시험', 'ACTIVE')", id);
        return id;
    }
}
