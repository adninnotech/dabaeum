package com.adn.dabaeum.user.application;

import com.adn.dabaeum.user.domain.User;
import java.util.List;

public record UserPage(
    List<User> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
