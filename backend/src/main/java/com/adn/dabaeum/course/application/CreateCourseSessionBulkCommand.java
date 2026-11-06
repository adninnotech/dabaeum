package com.adn.dabaeum.course.application;

import java.util.List;

/**
 * @param appendAfterExisting 반복 규칙으로 펼친 회차를 기존 회차 뒤에 붙인다. 펼친 회차는 1부터 매겨져 있으므로
 *                            서비스가 기존 최대 회차 번호만큼 밀어 준다. 직접 입력한 회차는 요청 번호를 그대로 쓴다.
 */
public record CreateCourseSessionBulkCommand(
    List<CreateCourseSessionCommand> sessions,
    boolean replaceExisting,
    boolean appendAfterExisting
) {

    public CreateCourseSessionBulkCommand(List<CreateCourseSessionCommand> sessions, boolean replaceExisting) {
        this(sessions, replaceExisting, false);
    }

    public static CreateCourseSessionBulkCommand appendingRepeat(List<CreateCourseSessionCommand> sessions) {
        return new CreateCourseSessionBulkCommand(sessions, false, true);
    }
}
