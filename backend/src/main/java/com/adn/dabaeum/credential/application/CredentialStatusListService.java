package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 공개 상태 리스트 VC 를 요청 시점의 DB 상태로 만든다.
 *
 * <p>폐기 진실은 원장과 tb_credentials 에 있고 리스트는 그 투영이다. 별도 저장본을 두지 않고
 * 매번 다시 만들어 갱신 누락이 생기지 않게 한다.
 */
@Service
public class CredentialStatusListService {

    private final CredentialStatusListRepository repository;
    private final CredentialStatusListDocumentFactory documentFactory;
    private final BitstringStatusListEncoder encoder;
    private final CredentialStatusListProofService proofService;
    private final Clock clock;

    @Autowired
    public CredentialStatusListService(
        CredentialStatusListRepository repository,
        CredentialStatusListDocumentFactory documentFactory,
        BitstringStatusListEncoder encoder,
        ObjectProvider<CredentialStatusListProofService> proofService,
        Clock clock
    ) {
        this(repository, documentFactory, encoder, proofService.getIfAvailable(), clock);
    }

    public CredentialStatusListService(
        CredentialStatusListRepository repository,
        CredentialStatusListDocumentFactory documentFactory,
        BitstringStatusListEncoder encoder,
        CredentialStatusListProofService proofService,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.documentFactory = Objects.requireNonNull(documentFactory, "documentFactory");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.proofService = proofService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SignedCredentialEnvelope document(UUID listId) {
        Objects.requireNonNull(listId, "listId");
        if (proofService == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.INTERNAL_SERVER_ERROR,
                "Credential status list signing service is unavailable");
        }
        CredentialStatusList list = repository.findById(listId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                ApiErrorCode.CREDENTIAL_NOT_FOUND, "Credential status list not found"));
        List<Integer> revoked = repository.findRevokedIndexes(list.id());
        String encodedList = encoder.encode(list.capacity(), revoked);
        CredentialDocument document = documentFactory.create(
            list.id(), list.institutionId(), clock.instant(), encodedList);
        return proofService.signStatusList(document);
    }
}
