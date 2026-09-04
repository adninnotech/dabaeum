package com.adn.dabaeum.support.application;

import com.adn.dabaeum.support.domain.Notice;
import java.util.List;

public record NoticePage(
    List<Notice> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
