package com.adn.dabaeum.institution.infrastructure.mybatis;

import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequest;
import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequestRepository;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class InstitutionSigningKeyRequestMyBatisRepository
    implements InstitutionSigningKeyRequestRepository {

    private final InstitutionSigningKeyRequestMapper mapper;

    public InstitutionSigningKeyRequestMyBatisRepository(InstitutionSigningKeyRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(InstitutionSigningKeyRequest request) {
        mapper.insert(Objects.requireNonNull(request, "request"));
    }
}
