package com.adn.dabaeum.correction.infrastructure.mybatis;

import com.adn.dabaeum.correction.domain.AdminCorrection;
import com.adn.dabaeum.correction.domain.AdminCorrectionRepository;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class AdminCorrectionMyBatisRepository implements AdminCorrectionRepository {

    private final AdminCorrectionMapper mapper;

    public AdminCorrectionMyBatisRepository(AdminCorrectionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void insert(AdminCorrection correction) {
        mapper.insert(new AdminCorrectionRow(
            correction.id(), correction.targetType().name(), correction.targetId(),
            correction.fromStatus(), correction.toStatus(), correction.reason(),
            correction.correctedBy(), correction.correctedAt()));
    }
}
