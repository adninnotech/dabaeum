package com.adn.dabaeum.credential.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dabaeum.credential-proof")
public class CredentialProofProperties {

    public static final String DEVELOPMENT_KEY_ID =
        "urn:dabaeum:credential-key:development-1";

    private String keyId = DEVELOPMENT_KEY_ID;
    private String privateKeyPath = "";
    private String publicKeyPath = "";
    private CredentialProofProvider provider = CredentialProofProvider.LOCAL;
    private final OpenBao openbao = new OpenBao();

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        if (!DEVELOPMENT_KEY_ID.equals(keyId)) {
            throw new IllegalArgumentException("credential proof key id is invalid");
        }
        this.keyId = keyId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath == null ? "" : privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath == null ? "" : publicKeyPath;
    }

    public CredentialProofProvider getProvider() {
        return provider;
    }

    public void setProvider(CredentialProofProvider provider) {
        this.provider = provider == null ? CredentialProofProvider.LOCAL : provider;
    }

    public OpenBao getOpenbao() {
        return openbao;
    }

    /** provider=openbao 일 때의 접속·역할 설정. 역할별 Role ID·Secret ID 는 환경변수로만 들어온다. */
    public static class OpenBao {

        private String uri = "http://127.0.0.1:8200";
        private String signMount = "education-sign";
        private String statusMount = "education-status";
        private String appRolePath = "approle";
        private final AppRole signing = new AppRole();
        private final AppRole status = new AppRole();
        private final AppRole keyAdmin = new AppRole();

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getSignMount() {
            return signMount;
        }

        public void setSignMount(String signMount) {
            this.signMount = signMount;
        }

        public String getStatusMount() {
            return statusMount;
        }

        public void setStatusMount(String statusMount) {
            this.statusMount = statusMount;
        }

        public String getAppRolePath() {
            return appRolePath;
        }

        public void setAppRolePath(String appRolePath) {
            this.appRolePath = appRolePath;
        }

        public AppRole getSigning() {
            return signing;
        }

        public AppRole getStatus() {
            return status;
        }

        public AppRole getKeyAdmin() {
            return keyAdmin;
        }
    }

    public static class AppRole {

        private String roleId = "";
        private String secretId = "";

        public String getRoleId() {
            return roleId;
        }

        public void setRoleId(String roleId) {
            this.roleId = roleId == null ? "" : roleId;
        }

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId == null ? "" : secretId;
        }
    }
}
