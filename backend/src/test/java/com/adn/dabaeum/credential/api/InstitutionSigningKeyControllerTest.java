package com.adn.dabaeum.credential.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.credential.application.KmsOperationAdjudicationService;
import com.adn.dabaeum.credential.application.SigningKeyCompromiseService;
import com.adn.dabaeum.credential.application.SigningKeyException;
import com.adn.dabaeum.credential.application.SigningKeyLifecycleService;
import com.adn.dabaeum.credential.application.SigningKeyOverview;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningService;
import com.adn.dabaeum.credential.application.SigningKeyRotationService;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(InstitutionSigningKeyController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=signing-key-test-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class,
    SigningKeyApiMapper.class
})
class InstitutionSigningKeyControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final UUID INSTITUTION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BASE = "/api/v1/institutions/" + INSTITUTION + "/signing-keys";
    private static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SigningKeyProvisioningService provisioning;

    @MockitoBean
    SigningKeyRotationService rotation;

    @MockitoBean
    SigningKeyLifecycleService lifecycle;

    @MockitoBean
    SigningKeyCompromiseService compromiseService;

    @MockitoBean
    KmsOperationAdjudicationService adjudication;

    private InstitutionSignKey master;
    private SigningKeyOverview overview;

    @BeforeEach
    void overview() {
        master = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(1, NOW);
        InstitutionSignKeyVersion version = InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(),
            INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 1, JWK, NOW, ADMIN, NOW);
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.CREATE, null, "개설",
            ADMIN, null, "CREATE-1/credential", NOW).recorded(1, "{\"status\":\"ACTIVE\"}", NOW).completed(NOW);
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            "a".repeat(64), ADMIN, NOW);
        overview = new SigningKeyOverview(INSTITUTION,
            List.of(new SigningKeyOverview.KeyView(master, List.of(new SigningKeyOverview.VersionView(version,
                "https://vc.example.test/api/v1/vc/issuers/" + INSTITUTION + "#v1")))),
            List.of(operation), List.of(approval));
        when(lifecycle.overview(INSTITUTION)).thenReturn(overview);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getReturnsKeysVersionsKidOperationsAndOpenApprovals() throws Exception {
        mockMvc.perform(get(BASE).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionId").value(INSTITUTION.toString()))
            .andExpect(jsonPath("$.data.keys[0].purpose").value("credential"))
            .andExpect(jsonPath("$.data.keys[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.keys[0].currentVersion").value(1))
            .andExpect(jsonPath("$.data.keys[0].versions[0].kid").value(org.hamcrest.Matchers.endsWith("#v1")))
            .andExpect(jsonPath("$.data.keys[0].versions[0].publicKeyJwk.kty").value("OKP"))
            .andExpect(jsonPath("$.data.keys[0].versions[0].publicKeyJwk.kid").doesNotExist())
            .andExpect(jsonPath("$.data.operations[0].action").value("CREATE"))
            .andExpect(jsonPath("$.data.operations[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.openApprovals[0].action").value("ROTATE"))
            .andExpect(jsonPath("$.data.openApprovals[0].paramsHash").doesNotExist());
    }

    @Test
    void createReturns201WithTheOverview() throws Exception {
        when(provisioning.createKeys(any())).thenReturn(overview.operations());

        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"CREATE-1\",\"reason\":\"개설\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.keys[0].status").value("ACTIVE"));
    }

    @Test
    void approvalRotateSuspendResumeRevokeMapToTheServices() throws Exception {
        when(lifecycle.createApproval(any())).thenReturn(overview.openApprovals().get(0));
        when(rotation.rotate(any())).thenReturn(overview.operations().get(0));
        when(lifecycle.suspend(any())).thenReturn(overview.operations().get(0));
        when(lifecycle.resume(any())).thenReturn(overview.operations().get(0));
        when(lifecycle.revoke(any())).thenReturn(overview.operations().get(0));
        UUID approvalId = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/approvals").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"credential\",\"action\":\"ROTATE\",\"idempotencyKey\":\"ROTATE-2027\","
                    + "\"reason\":\"정기 교체\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.action").value("ROTATE"))
            .andExpect(jsonPath("$.data.expectedVersion").value(1));
        mockMvc.perform(post(BASE + "/credential/rotate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalId\":\"" + approvalId + "\",\"idempotencyKey\":\"ROTATE-2027\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/status-list/rotate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalId\":\"" + approvalId + "\",\"idempotencyKey\":\"ROTATE-S\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/credential/suspend").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"SUSPEND-1\",\"reason\":\"감사 지적\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/credential/resume").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalId\":\"" + approvalId + "\",\"idempotencyKey\":\"RESUME-1\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/credential/revoke").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalId\":\"" + approvalId + "\",\"idempotencyKey\":\"REVOKE-1\",\"reason\":\"폐지\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void approvalWithNegativeKeyVersionIsRejected() throws Exception {
        // keyVersion 은 yaml 에서도 minimum: 0 이라 이 요청 자체가 스키마 위반이다 — 요청 검증이
        // 응답 코드와 무관하게 실패하므로 openApi().isValid() 는 쓰지 않는다(/other/rotate 와 같은 이유).
        mockMvc.perform(post(BASE + "/approvals").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"credential\",\"action\":\"COMPROMISE\",\"idempotencyKey\":\"COMPROMISE-1\","
                    + "\"keyVersion\":-1,\"compromisedAt\":\"2026-09-11T00:00:00Z\",\"reason\":\"유출 선언\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void serviceFailuresMapToStatusAndCodes() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(rotation.rotate(any()))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.APPROVAL_REJECTED, "approval mismatch"))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.KMS_UNAVAILABLE, "unconfirmed", operationId))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.NOT_FOUND, "sign key not found"))
            .thenThrow(new SigningKeyException(
                SigningKeyException.Kind.IDEMPOTENCY_CONFLICT, "idempotency key reused"));
        when(provisioning.createKeys(any()))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.ALREADY_EXISTS, "exists"));
        when(lifecycle.suspend(any()))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.STATE_CONFLICT, "not active"));
        String rotate = "{\"approvalId\":\"" + UUID.randomUUID() + "\",\"idempotencyKey\":\"ROTATE-2027\"}";

        mockMvc.perform(post(BASE + "/credential/rotate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(rotate))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_APPROVAL_REJECTED"));
        mockMvc.perform(post(BASE + "/credential/rotate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(rotate))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("KMS_UNAVAILABLE"))
            .andExpect(jsonPath("$.details[0]").value(operationId.toString()));
        mockMvc.perform(post(BASE + "/credential/rotate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(rotate))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_NOT_FOUND"));
        mockMvc.perform(post(BASE + "/credential/rotate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(rotate))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_IDEMPOTENCY_CONFLICT"));
        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"CREATE-1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_ALREADY_EXISTS"));
        mockMvc.perform(post(BASE + "/credential/suspend").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"SUSPEND-1\",\"reason\":\"감사\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_STATE_CONFLICT"));
        mockMvc.perform(post(BASE + "/other/rotate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(rotate))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        // reason 은 @NotBlank 로 검증하므로 빠지면 이 프로젝트의 공통 규칙대로 422 VALIDATION_FAILED 다
        // (IllegalArgumentException 을 통한 400 BAD_REQUEST 와는 다른 경로다). 이 요청 자체가 필수
        // 속성 reason 이 빠진 스키마 위반이라 openApi().isValid() 는 /other/rotate 와 같은 이유로 쓰지 않는다.
        mockMvc.perform(post(BASE + "/credential/suspend").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"SUSPEND-2\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void compromiseAndAdjudicateMapToTheServicesAndReturnTheOverview() throws Exception {
        when(compromiseService.compromise(any())).thenReturn(overview.operations().get(0));
        when(adjudication.adjudicate(any())).thenReturn(overview.operations().get(0));
        UUID approvalId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/credential/compromise").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalId\":\"" + approvalId + "\",\"keyVersion\":1,"
                    + "\"compromisedAt\":\"2026-09-13T12:00:00Z\",\"idempotencyKey\":\"COMPROMISE-1\","
                    + "\"reason\":\"노트북 분실\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.keys[0].status").value("ACTIVE"));
        mockMvc.perform(post(BASE + "/status-list/adjudicate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"" + operationId + "\",\"decision\":\"RECORDED\","
                    + "\"evidence\":\"audit shows rotate by app token at 23:59Z\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void compromiseFailuresMapToStatusAndCodes() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(compromiseService.compromise(any()))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.APPROVAL_REJECTED, "approval mismatch"))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.KMS_UNAVAILABLE, "rotation unconfirmed",
                operationId))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.STATE_CONFLICT, "already compromised"));
        when(adjudication.adjudicate(any()))
            .thenThrow(new SigningKeyException(SigningKeyException.Kind.NOT_FOUND, "operation not found"));
        String compromise = "{\"approvalId\":\"" + UUID.randomUUID() + "\",\"keyVersion\":1,"
            + "\"compromisedAt\":\"2026-09-13T12:00:00Z\",\"idempotencyKey\":\"COMPROMISE-2\",\"reason\":\"유출\"}";
        String adjudicate = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"decision\":\"FAILED\","
            + "\"evidence\":\"no rotate request in audit range 2026-09-13\"}";

        mockMvc.perform(post(BASE + "/credential/compromise").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(compromise))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_APPROVAL_REJECTED"));
        mockMvc.perform(post(BASE + "/credential/compromise").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(compromise))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("KMS_UNAVAILABLE"))
            .andExpect(jsonPath("$.details[0]").value(operationId.toString()));
        mockMvc.perform(post(BASE + "/credential/compromise").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(compromise))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_STATE_CONFLICT"));
        mockMvc.perform(post(BASE + "/credential/adjudicate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(adjudicate))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_NOT_FOUND"));
        // decision 은 yaml enum 이라 이 요청 자체가 스키마 위반 — /other/rotate 와 같은 이유로 isValid 를 쓰지 않는다
        mockMvc.perform(post(BASE + "/credential/adjudicate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"decision\":\"MAYBE\","
                    + "\"evidence\":\"no rotate request in audit range\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        // evidence 10자 미만은 @Size 위반 → 422 (요청이 스키마 위반이라 isValid 없음)
        mockMvc.perform(post(BASE + "/credential/adjudicate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"decision\":\"FAILED\",\"evidence\":\"short\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post(BASE + "/credential/compromise").with(institutionAdmin())
                .contentType(MediaType.APPLICATION_JSON).content(compromise))
            .andExpect(status().isForbidden());
    }

    @Test
    void requiresPlatformAdmin() throws Exception {
        mockMvc.perform(get(BASE))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(BASE).with(institutionAdmin()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(BASE + "/credential/suspend").with(institutionAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"SUSPEND-1\",\"reason\":\"감사\"}"))
            .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor admin() {
        return principal("PLATFORM_ADMIN", null);
    }

    private static RequestPostProcessor institutionAdmin() {
        return principal("INSTITUTION_ADMIN", INSTITUTION);
    }

    private static RequestPostProcessor principal(String role, UUID institutionId) {
        AuthenticatedUserContext context = new AuthenticatedUserContext(ADMIN, "DADAEGU",
            Set.of(new AuthenticatedRole(role, institutionId)));
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(ADMIN, "DADAEGU", Set.of(role));
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, Set.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new AuthenticatedTokenDetails(NOW.plusSeconds(3600), context));
        return authentication(authentication);
    }
}
