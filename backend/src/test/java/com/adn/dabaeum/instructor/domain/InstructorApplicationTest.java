package com.adn.dabaeum.instructor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstructorApplicationTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSTITUTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REVIEWER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void normalizesPendingApplicationMessageAndRequiresNoReviewData() {
        InstructorApplication application = new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.PENDING,
            "  강의 경험이 있습니다.  ", null, null, NOW, null, NOW, NOW
        );

        assertThat(application.applicationMessage()).isEqualTo("강의 경험이 있습니다.");
        assertThatThrownBy(() -> new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.PENDING,
            null, null, REVIEWER_ID, NOW, NOW, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesApprovedAndRejectedStateInvariants() {
        assertThat(new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.APPROVED,
            null, null, REVIEWER_ID, NOW, NOW, NOW, NOW
        ).status()).isEqualTo(InstructorApplicationStatus.APPROVED);

        InstructorApplication rejected = new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.REJECTED,
            null, "  경력 확인 불가  ", REVIEWER_ID, NOW, NOW, NOW, NOW
        );
        assertThat(rejected.rejectionReason()).isEqualTo("경력 확인 불가");
        assertThatThrownBy(() -> new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.REJECTED,
            null, null, REVIEWER_ID, NOW, NOW, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMessagesLongerThanOneThousandCharacters() {
        assertThatThrownBy(() -> new InstructorApplication(
            ID, USER_ID, INSTITUTION_ID, InstructorApplicationStatus.PENDING,
            "가".repeat(1001), null, null, NOW, null, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
