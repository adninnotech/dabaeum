package com.adn.dabaeum.accesslog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 설계서 §10.2 — 실제 요청이 인터셉터를 거쳐 접속기록 한 줄을 남기는지 본다.
 * MockMvc 는 테스트와 같은 스레드에서 돌아 기록도 테스트 트랜잭션 안에 쓰이고 롤백된다.
 */
class AdminAccessLogRecordingIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void adminReadingAnotherUserLeavesOneRow() throws Exception {
        UUID admin = insertUser("접속기록 관리자");
        UUID target = insertUser("접속기록 대상");

        mockMvc.perform(get("/api/v1/users/{userId}", target).with(platformAdmin(admin)))
            .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM tb_admin_access_logs
             WHERE actor_id = ?
               AND actor_role = 'PLATFORM_ADMIN'
               AND action = 'READ'
               AND endpoint = 'GET /api/v1/users/{userId}'
               AND target_user_ids = ARRAY[?]::uuid[]
               AND result_count = 1
               AND status_code = 200
            """, Integer.class, admin, target)).isEqualTo(1);
    }

    @Test
    void adminReadingThemselvesLeavesNothing() throws Exception {
        UUID admin = insertUser("본인 조회 관리자");

        mockMvc.perform(get("/api/v1/users/{userId}", admin).with(platformAdmin(admin)))
            .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_admin_access_logs WHERE actor_id = ?", Integer.class, admin)).isZero();
    }

    @Test
    void failedLookupIsAlsoRecorded() throws Exception {
        UUID admin = insertUser("실패 조회 관리자");
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}", missing).with(platformAdmin(admin)))
            .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM tb_admin_access_logs
             WHERE actor_id = ? AND status_code = 404 AND target_user_ids = ARRAY[?]::uuid[]
            """, Integer.class, admin, missing)).isEqualTo(1);
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private static RequestPostProcessor platformAdmin(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(userId, Set.of("PLATFORM_ADMIN")),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
    }
}
