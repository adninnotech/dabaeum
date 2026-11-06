package com.adn.dabaeum.user.infrastructure.mybatis;

import com.adn.dabaeum.user.domain.AgreementType;
import com.adn.dabaeum.user.domain.UserAgreement;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class UserAgreementMyBatisRepository implements UserAgreementRepository {

    private final UserAgreementMapper mapper;

    public UserAgreementMyBatisRepository(UserAgreementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void saveAll(List<UserAgreement> agreements) {
        agreements.forEach(agreement -> mapper.insert(toRow(agreement)));
    }

    @Override
    public List<UserAgreement> findByUserId(UUID userId) {
        return mapper.selectByUserId(userId).stream()
            .map(this::toDomain)
            .toList();
    }

    private UserAgreementRow toRow(UserAgreement agreement) {
        return new UserAgreementRow(
            agreement.id(),
            agreement.userId(),
            agreement.agreementType().name(),
            agreement.agreed(),
            agreement.agreedAt()
        );
    }

    private UserAgreement toDomain(UserAgreementRow row) {
        return new UserAgreement(
            row.id(),
            row.userId(),
            AgreementType.valueOf(row.agreementType()),
            row.agreed(),
            row.agreedAt()
        );
    }
}
