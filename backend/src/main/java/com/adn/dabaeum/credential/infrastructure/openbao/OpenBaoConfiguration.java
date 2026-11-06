package com.adn.dabaeum.credential.infrastructure.openbao;

import com.adn.dabaeum.credential.domain.StatusListSigningPort;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.credential.domain.TransitSigningPort;
import com.adn.dabaeum.credential.infrastructure.crypto.CredentialProofProperties;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * provider=openbao 일 때만 활성. 백엔드 프로세스 하나가 AppRole 세 개로 로그인해 토큰 세 개를 든다
 * (설계서 §13.1). 로그인은 첫 요청 때 일어나므로 빈 생성만으로는 OpenBao 에 접속하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dabaeum.credential-proof", name = "provider", havingValue = "openbao")
public class OpenBaoConfiguration {

    /**
     * AppRole 토큰(TTL 15분, 최대 1시간)을 만료 전에 갱신하고, 갱신할 수 없으면 다시 로그인한다.
     * 한 번만 로그인하는 SimpleSessionManager 로는 최대 TTL 뒤 모든 요청이 403 이 된다.
     * 갱신 스케줄러는 OpenBaoClients 가 소유하고 컨텍스트 빈으로 노출하지 않는다 — TaskScheduler 타입 빈이
     * 하나 더 생기면 다른 모듈의 타입 주입이 후보 2개로 기동에 실패한다(2026-09-16 S3 스위치에서 발생).
     */
    @Bean(destroyMethod = "close")
    OpenBaoClients openBaoClients(CredentialProofProperties properties) {
        CredentialProofProperties.OpenBao openbao = properties.getOpenbao();
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(openbao.getUri()));
        String appRolePath = openbao.getAppRolePath();
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("openbao-session-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        try {
            return new OpenBaoClients(
                template(endpoint, appRolePath, "signing", openbao.getSigning(), scheduler),
                template(endpoint, appRolePath, "status", openbao.getStatus(), scheduler),
                template(endpoint, appRolePath, "key-admin", openbao.getKeyAdmin(), scheduler),
                scheduler);
        } catch (RuntimeException e) {
            scheduler.shutdown();
            throw e;
        }
    }

    /** 수료증 서명은 발급 역할 토큰만 쓴다 (§13.3). */
    @Bean
    TransitSigningPort transitSigningPort(OpenBaoClients clients) {
        return new OpenBaoTransitSigningAdapter(clients.signing());
    }

    /** Status List 서명은 상태 역할 토큰만 쓴다 (§9.1, §13.3). 발급 역할 포트와 타입을 나눠 섞이지 않게 한다. */
    @Bean
    StatusListSigningPort statusListSigningPort(OpenBaoClients clients) {
        OpenBaoTransitSigningAdapter adapter = new OpenBaoTransitSigningAdapter(clients.status());
        return adapter::sign;
    }

    /** 키 생성·회전·config 는 키 관리자 역할 토큰만 쓴다 (§13.3, §13.4). */
    @Bean
    TransitKeyAdminPort transitKeyAdminPort(OpenBaoClients clients) {
        return new OpenBaoTransitKeyAdminAdapter(clients.keyAdmin());
    }

    private static VaultTemplate template(
        VaultEndpoint endpoint,
        String appRolePath,
        String role,
        CredentialProofProperties.AppRole appRole,
        TaskScheduler scheduler
    ) {
        if (appRole.getRoleId().isBlank() || appRole.getSecretId().isBlank()) {
            throw new IllegalStateException(
                "openbao " + role + " role-id and secret-id are required (ADR-0006)");
        }
        ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
            .path(appRolePath)
            .roleId(RoleId.provided(appRole.getRoleId()))
            .secretId(SecretId.provided(appRole.getSecretId()))
            .build();
        RestTemplate restTemplate = VaultClients.createRestTemplate(endpoint, requestFactory);
        AppRoleAuthentication authentication = new AppRoleAuthentication(options, restTemplate);
        return new VaultTemplate(endpoint, requestFactory,
            new LifecycleAwareSessionManager(authentication, scheduler, restTemplate));
    }
}
