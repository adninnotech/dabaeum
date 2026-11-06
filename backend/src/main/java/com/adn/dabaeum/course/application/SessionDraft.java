package com.adn.dabaeum.course.application;

import java.time.LocalDate;
import java.time.LocalTime;

public record SessionDraft(
    int sessionNo,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime
) {
}
