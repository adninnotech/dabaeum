package com.adn.dabaeum.instructor.application;

import java.util.List;

public record InstructorApplicationPage(
    List<InstructorApplicationView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public InstructorApplicationPage {
        data = List.copyOf(data);
    }
}
