package com.adn.dabaeum.support.application;

import com.adn.dabaeum.support.domain.Faq;
import java.util.List;

public record FaqPage(
    List<Faq> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
