package com.adn.dabaeum.support.domain;

import java.util.List;

public interface FaqRepository {

    List<Faq> findPage(int limit, int offset);

    long countAll();
}
