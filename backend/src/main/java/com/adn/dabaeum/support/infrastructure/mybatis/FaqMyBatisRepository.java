package com.adn.dabaeum.support.infrastructure.mybatis;

import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.FaqRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class FaqMyBatisRepository implements FaqRepository {

    private final SupportMapper mapper;

    public FaqMyBatisRepository(SupportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Faq> findPage(int limit, int offset) {
        return mapper.selectFaqs(limit, offset).stream()
            .map(row -> new Faq(row.id(), row.question(), row.answer(),
                row.sortOrder() == null ? 0 : row.sortOrder(),
                row.createdAt(), row.updatedAt()))
            .toList();
    }

    @Override
    public long countAll() {
        return mapper.countFaqs();
    }
}
