package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.UUID;

public interface CredentialDocumentDownloadService {

    Download download(UUID credentialId, AuthenticatedUserContext actor);

    record Download(String credentialNo, String compactJws) {
        public Download {
            if (credentialNo == null || credentialNo.isBlank()) {
                throw new IllegalArgumentException("credentialNo is required");
            }
            if (compactJws == null || compactJws.isBlank()) {
                throw new IllegalArgumentException("compactJws is required");
            }
        }
    }
}
