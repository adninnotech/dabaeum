package com.adn.dabaeum.user.application;

import java.util.List;

public record UserPage(
    List<UserView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
