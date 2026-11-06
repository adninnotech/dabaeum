package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.application.AttendSessionView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * 로그인하지 않은 학생 폰이 받는 출석 세션. 다대구 sessionKey·DID 암호문은 담지 않는다.
 * 딥링크는 세션을 여는 응답에만 싣고, 결과 조회에는 이름과 오류 코드만 싣는다.
 */
public record AttendSessionResponse(
    UUID attendId,
    String state,
    @JsonInclude(JsonInclude.Include.NON_NULL) String deepLink,
    @JsonInclude(JsonInclude.Include.NON_NULL) String studentName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode
) {

    static AttendSessionResponse opened(AttendSessionView view) {
        return new AttendSessionResponse(view.id(), view.state().name(), view.deepLink(), null, null);
    }

    static AttendSessionResponse status(AttendSessionView view) {
        return new AttendSessionResponse(
            view.id(), view.state().name(), null, view.studentName(), view.errorCode());
    }
}
