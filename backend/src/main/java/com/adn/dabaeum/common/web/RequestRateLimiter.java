package com.adn.dabaeum.common.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Single-process rolling-window limit. State resets on restart. */
public class RequestRateLimiter {

    private static final int MAX_KEYS = 100_000;
    private final Clock clock;
    private final Map<String, ArrayDeque<Instant>> requests = new HashMap<>();

    public RequestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Returns zero when allowed, otherwise the seconds until the oldest request expires. */
    public synchronized long allow(String key, int limit, Duration window) {
        Instant now = clock.instant();
        ArrayDeque<Instant> entries = requests.get(key);
        if (entries == null) {
            if (requests.size() >= MAX_KEYS) {
                requests.entrySet().removeIf(entry ->
                    entry.getValue().peekLast().isBefore(now.minus(Duration.ofHours(1))));
            }
            if (requests.size() >= MAX_KEYS) return 60;
            entries = new ArrayDeque<>();
            requests.put(key, entries);
        }
        Instant cutoff = now.minus(window);
        while (!entries.isEmpty() && !entries.peekFirst().isAfter(cutoff)) {
            entries.removeFirst();
        }
        if (entries.size() >= limit) {
            long millis = Duration.between(now, entries.peekFirst().plus(window)).toMillis();
            return Math.max(1, (millis + 999) / 1000);
        }
        entries.addLast(now);
        return 0;
    }
}
