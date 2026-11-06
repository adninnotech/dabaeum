package com.adn.dabaeum.course.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record RepeatRule(
    LocalDate firstDate,
    Set<DayOfWeek> daysOfWeek,
    LocalTime startTime,
    LocalTime endTime,
    int count
) {
}
