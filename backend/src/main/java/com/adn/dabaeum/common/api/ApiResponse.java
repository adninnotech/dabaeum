package com.adn.dabaeum.common.api;

public record ApiResponse<T>(T data, ApiMeta meta) {
}
