package com.adn.dabaeum.accesslog.domain;

import java.util.List;

public record AdminAccessLogPage(List<AdminAccessLogEntry> entries, long totalElements) {

    public AdminAccessLogPage {
        entries = List.copyOf(entries);
    }
}
