package com.adn.dabaeum.course.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** D-2 의 반복 규칙을 개별 회차로 펼친다. 12회차를 12번 POST 하지 않게 하려는 것. */
public final class SessionScheduleExpander {

    private static final int MAX_COUNT = 200;
    /** 무한 루프 방지. 주 1회 200 회차면 약 1400일이면 충분하다. */
    private static final int MAX_DAYS_SCANNED = 4000;

    private SessionScheduleExpander() {
    }

    public static List<SessionDraft> expand(RepeatRule rule) {
        validate(rule);
        List<SessionDraft> drafts = new ArrayList<>(rule.count());
        LocalDate cursor = rule.firstDate();
        int scanned = 0;
        while (drafts.size() < rule.count()) {
            if (scanned++ > MAX_DAYS_SCANNED) {
                throw new IllegalArgumentException(
                    "count is too large for the given daysOfWeek");
            }
            if (rule.daysOfWeek().contains(cursor.getDayOfWeek())) {
                drafts.add(new SessionDraft(
                    drafts.size() + 1, cursor, rule.startTime(), rule.endTime()));
            }
            cursor = cursor.plusDays(1);
        }
        return List.copyOf(drafts);
    }

    private static void validate(RepeatRule rule) {
        if (rule.daysOfWeek() == null || rule.daysOfWeek().isEmpty()) {
            throw new IllegalArgumentException("daysOfWeek must not be empty");
        }
        if (rule.count() < 1 || rule.count() > MAX_COUNT) {
            throw new IllegalArgumentException(
                "count must be between 1 and " + MAX_COUNT);
        }
        if (rule.startTime() == null || rule.endTime() == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (!rule.endTime().isAfter(rule.startTime())) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        if (rule.firstDate() == null) {
            throw new IllegalArgumentException("firstDate is required");
        }
    }
}
