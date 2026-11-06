package com.adn.dabaeum.user.infrastructure.mybatis;

import com.adn.dabaeum.user.domain.UserInterestRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class UserInterestMyBatisRepository implements UserInterestRepository {

    private final UserInterestMapper mapper;

    public UserInterestMyBatisRepository(UserInterestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> findCodes(UUID userId) {
        return mapper.selectCodes(userId);
    }

    @Override
    public void replace(UUID userId, List<String> codes, Instant now) {
        mapper.deleteByUserId(userId);
        codes.forEach(code -> mapper.insert(userId, code, now));
    }
}
