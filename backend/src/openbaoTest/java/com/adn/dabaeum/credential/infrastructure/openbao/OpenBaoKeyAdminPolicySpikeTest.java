package com.adn.dabaeum.credential.infrastructure.openbao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.domain.SignKeyNaming;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * 키 관리자 AppRole 토큰으로 §13.4 Policy 를 실 OpenBao 에서 확인한다 (§22 "관리자 Policy 로 min_decryption_version·
 * trim·삭제·export 가 거부된다", §19 항목 16 세대 키 이름). poc-check.sh 항목 5·6 을 JUnit 으로 옮긴 것.
 * 운영 mount 에 spike-* 키를 만든다 (§18.3 예외, 계획 7 결정 4). 키는 지우지 않는다(Policy 가 삭제를 막는다).
 */
@EnabledIfEnvironmentVariable(named = "BAO_ADDR", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BAO_KEY_ADMIN_ROLE_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BAO_KEY_ADMIN_SECRET_ID", matches = ".+")
class OpenBaoKeyAdminPolicySpikeTest {

    private static final String MOUNT = "education-sign";
    private static final UUID SPIKE_INSTITUTION = UUID.fromString("00000000-0000-4000-8000-00000000c0de");

    private final VaultOperations keyAdmin = appRole(System.getenv("BAO_KEY_ADMIN_ROLE_ID"),
        System.getenv("BAO_KEY_ADMIN_SECRET_ID"));

    static VaultOperations appRole(String roleId, String secretId) {
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(System.getenv("BAO_ADDR")));
        RestTemplate rest = VaultClients.createRestTemplate(endpoint, new SimpleClientHttpRequestFactory());
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder().path("approle")
            .roleId(RoleId.provided(roleId)).secretId(SecretId.provided(secretId)).build();
        return new VaultTemplate(endpoint, new SimpleClientHttpRequestFactory(),
            new SimpleSessionManager(new AppRoleAuthentication(options, rest)));
    }

    @Test
    void 세대_키_이름은_keys_plus_Policy_를_통과하고_생성_재조회_회전_config_가_된다() {
        OpenBaoTransitKeyAdminAdapter adapter = new OpenBaoTransitKeyAdminAdapter(keyAdmin);
        String name = "spike-" + SignKeyNaming.keyName(SPIKE_INSTITUTION, 2);   // spike-institution-…-g2

        adapter.createKey(MOUNT, name);
        assertThat(adapter.readKey(MOUNT, name).orElseThrow().matchesDesignDefaults()).isTrue();
        adapter.rotateKey(MOUNT, name);
        adapter.setMinEncryptionVersion(MOUNT, name, 2);
        assertThat(adapter.readKey(MOUNT, name).orElseThrow().minEncryptionVersion()).isEqualTo(2);
    }

    @Test
    void 키_관리자_토큰으로_exportable_명시_생성_trim_min_decryption_version_삭제는_거부된다() {
        OpenBaoTransitKeyAdminAdapter adapter = new OpenBaoTransitKeyAdminAdapter(keyAdmin);
        String name = "spike-policy-ed25519";   // 고정 이름: Policy 가 삭제를 막으므로 반복 실행에도 키가 늘지 않게
        adapter.createKey(MOUNT, name);

        assertThatThrownBy(() -> keyAdmin.write(MOUNT + "/keys/" + name + "-x",
            Map.of("type", "ed25519", "exportable", false)))
            .as("denied_parameters: exportable 를 명시하면 값과 무관하게 거부").hasMessageContaining("403");
        assertThatThrownBy(() -> keyAdmin.write(MOUNT + "/keys/" + name + "/trim", Map.of("min_available_version", 1)))
            .hasMessageContaining("403");
        assertThatThrownBy(() -> keyAdmin.write(MOUNT + "/keys/" + name + "/config", Map.of("min_decryption_version", 1)))
            .hasMessageContaining("403");
        assertThatThrownBy(() -> keyAdmin.delete(MOUNT + "/keys/" + name)).hasMessageContaining("403");
        assertThat(adapter.readKey(MOUNT, name)).as("거부된 뒤에도 키는 그대로").isPresent();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BAO_SIGNING_ROLE_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "BAO_SIGNING_SECRET_ID", matches = ".+")
    void 발급_역할_토큰으로는_회전과_status_mount_서명이_거부된다() {
        VaultOperations signing = appRole(System.getenv("BAO_SIGNING_ROLE_ID"), System.getenv("BAO_SIGNING_SECRET_ID"));
        assertThatThrownBy(() -> signing.write(MOUNT + "/keys/spike-ed25519/rotate", Map.of())).hasMessageContaining("403");
        assertThatThrownBy(() -> signing.write("education-status/sign/spike-ed25519",
            Map.of("input", "AA==", "key_version", 1))).hasMessageContaining("403");
    }
}
