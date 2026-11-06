package com.adn.dabaeum.administrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.administrator.application.AdministratorService;
import com.adn.dabaeum.administrator.application.CreateAdministratorCommand;
import com.adn.dabaeum.administrator.application.CreatedAdministrator;
import com.adn.dabaeum.administrator.domain.Administrator;
import com.adn.dabaeum.administrator.domain.AdministratorPage;
import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.authentication.application.LoginCommand;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §10.1·§10.3 — 관리자 생성부터 임시 비밀번호 로그인·변경 안내까지 실제 DB 로. */
class AdministratorIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired AdministratorService service;
    @Autowired LocalAccountApplicationService localAccounts;

    @Test
    void createdInstitutionAdminCanLogInWithTemporaryPasswordAndIsAskedToChangeIt() {
        UUID institutionId = insertInstitution();
        String email = "admin-" + UUID.randomUUID() + "@example.com";

        CreatedAdministrator created = service.create(new CreateAdministratorCommand(
            "기관 관리자", email, UserRole.INSTITUTION_ADMIN, institutionId));

        Administrator administrator = created.administrator();
        assertThat(administrator.passwordChangedAt()).isNull();
        assertThat(administrator.roles()).singleElement().satisfies(role -> {
            assertThat(role.role()).isEqualTo(UserRole.INSTITUTION_ADMIN);
            assertThat(role.institutionId()).isEqualTo(institutionId);
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_user_agreements WHERE user_id = ?", Integer.class, administrator.userId()))
            .isZero();

        AdministratorPage page = service.list(0, 20, UserRole.INSTITUTION_ADMIN, null, email);
        assertThat(page.administrators()).extracting(Administrator::userId).containsExactly(administrator.userId());

        LocalAuthResult login = localAccounts.login(new LoginCommand(email, created.temporaryPassword()));
        assertThat(login.userId()).isEqualTo(administrator.userId());
        assertThat(login.passwordExpired()).isTrue();
    }

    @Test
    void sameEmailTwiceConflicts() {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        service.create(new CreateAdministratorCommand("플랫폼 관리자", email, UserRole.PLATFORM_ADMIN, null));

        assertThatThrownBy(() -> service.create(
            new CreateAdministratorCommand("다른 이름", email, UserRole.PLATFORM_ADMIN, null)))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.IDENTITY_CONFLICT));
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        String code = "adm" + id.toString().substring(0, 8);
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)", id, code, "관리자 기관 " + code);
        return id;
    }
}
