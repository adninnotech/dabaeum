package com.adn.dabaeum.support.infrastructure.mybatis;

import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.FaqRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countAll() {
        return mapper.countFaqs();
    }

    @Override
    public void save(Faq faq) {
        mapper.insertFaq(toRow(faq));
    }

    @Override
    public Optional<Faq> findById(UUID id) {
        return Optional.ofNullable(mapper.selectFaqById(id)).map(this::toDomain);
    }

    @Override
    public boolean update(Faq faq) {
        return mapper.updateFaq(toRow(faq)) == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return mapper.deleteFaq(id) == 1;
    }

    private FaqRow toRow(Faq faq) {
        return new FaqRow(faq.id(), faq.question(), faq.answer(), faq.sortOrder(),
            faq.createdAt(), faq.updatedAt());
    }

    private Faq toDomain(FaqRow row) {
        return new Faq(row.id(), row.question(), row.answer(),
            row.sortOrder() == null ? 0 : row.sortOrder(),
            row.createdAt(), row.updatedAt());
    }
}
