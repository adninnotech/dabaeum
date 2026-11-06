package com.adn.dabaeum.support.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FaqRepository {

    List<Faq> findPage(int limit, int offset);

    long countAll();

    void save(Faq faq);

    Optional<Faq> findById(UUID id);

    boolean update(Faq faq);

    boolean delete(UUID id);
}
