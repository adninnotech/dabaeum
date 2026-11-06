package com.adn.dabaeum.statistics.domain;

public record BadgeStatRow(String key, String label, long issued, long revoked) {

    public BadgeStatRow withLabel(String value) {
        return new BadgeStatRow(key, value, issued, revoked);
    }
}
