package com.adn.dabaeum.accesslog.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLog;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AdminAccessLogFactoryTest {

    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID INSTITUTION = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void platformAdminReadingAnotherUserIsLogged() {
        AdminAccessLog log = AdminAccessLogFactory.create(event(admin("PLATFORM_ADMIN", null), TARGET, null, 200))
            .orElseThrow();

        assertThat(log.actorId()).isEqualTo(ACTOR);
        assertThat(log.actorRole()).isEqualTo("PLATFORM_ADMIN");
        assertThat(log.action()).isEqualTo(AccessLogAction.READ);
        assertThat(log.endpoint()).isEqualTo("GET /api/v1/users/{userId}");
        assertThat(log.targetUserIds()).containsExactly(TARGET);
        assertThat(log.resultCount()).isEqualTo(1);
        assertThat(log.statusCode()).isEqualTo(200);
    }

    @Test
    void platformRoleWinsOverInstitutionRole() {
        AuthenticatedUserContext both = new AuthenticatedUserContext(ACTOR, "LOCAL", Set.of(
            new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION), new AuthenticatedRole("PLATFORM_ADMIN", null)));

        assertThat(AdminAccessLogFactory.create(event(both, TARGET, null, 200)).orElseThrow().actorRole())
            .isEqualTo("PLATFORM_ADMIN");
    }

    @Test
    void nonAdminIsNotLogged() {
        AuthenticatedUserContext instructor = new AuthenticatedUserContext(ACTOR, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION)));

        assertThat(AdminAccessLogFactory.create(event(instructor, TARGET, null, 200))).isEmpty();
    }

    @Test
    void adminReadingOnlyThemselvesIsNotLogged() {
        assertThat(AdminAccessLogFactory.create(event(admin("PLATFORM_ADMIN", null), ACTOR, null, 200))).isEmpty();
    }

    @Test
    void listMergesPathAndBodyTargetsAndKeepsRowCount() {
        UUID other = UUID.randomUUID();
        CapturedTargets captured = new CapturedTargets(new LinkedHashSet<>(Set.of(other)), 3);

        AdminAccessLog log = AdminAccessLogFactory.create(event(admin("INSTITUTION_ADMIN", INSTITUTION), null, captured, 200))
            .orElseThrow();

        assertThat(log.targetUserIds()).containsExactly(other);
        assertThat(log.resultCount()).isEqualTo(3);
    }

    @Test
    void countOnlyListKeepsNullTargets() {
        AdminAccessLog log = AdminAccessLogFactory.create(
            event(admin("PLATFORM_ADMIN", null), null, new CapturedTargets(Set.of(), 7), 200)).orElseThrow();

        assertThat(log.targetUserIds()).isNull();
        assertThat(log.resultCount()).isEqualTo(7);
    }

    @Test
    void moreThanThousandTargetsAreDroppedButCounted() {
        Set<UUID> many = new LinkedHashSet<>();
        IntStream.range(0, 1001).forEach(i -> many.add(UUID.randomUUID()));

        AdminAccessLog log = AdminAccessLogFactory.create(
            event(admin("PLATFORM_ADMIN", null), null, new CapturedTargets(many, 1001), 200)).orElseThrow();

        assertThat(log.targetUserIds()).isNull();
        assertThat(log.resultCount()).isEqualTo(1001);
    }

    @Test
    void failedResponseIsLoggedWithPathTarget() {
        AdminAccessLog log = AdminAccessLogFactory.create(event(admin("PLATFORM_ADMIN", null), TARGET, null, 404))
            .orElseThrow();

        assertThat(log.statusCode()).isEqualTo(404);
        assertThat(log.targetUserIds()).containsExactly(TARGET);
    }

    @Test
    void longUserAgentIsTruncated() {
        AdminAccessLogFactory.AccessEvent event = event(admin("PLATFORM_ADMIN", null), TARGET, null, 200)
            .withUserAgent("x".repeat(600));

        assertThat(AdminAccessLogFactory.create(event).orElseThrow().userAgent()).hasSize(500);
    }

    private static AuthenticatedUserContext admin(String role, UUID institutionId) {
        return new AuthenticatedUserContext(ACTOR, "LOCAL", Set.of(new AuthenticatedRole(role, institutionId)));
    }

    private static AdminAccessLogFactory.AccessEvent event(
        AuthenticatedUserContext actor, UUID pathUserId, CapturedTargets captured, int status
    ) {
        return new AdminAccessLogFactory.AccessEvent(
            UUID.randomUUID(), actor, AccessLogAction.READ, "GET /api/v1/users/{userId}", pathUserId, captured,
            status, "203.0.113.7", "junit", UUID.randomUUID(), NOW);
    }
}
