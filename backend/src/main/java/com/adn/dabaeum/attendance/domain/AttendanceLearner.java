package com.adn.dabaeum.attendance.domain;

import java.util.UUID;

/** 출결 목록에 함께 싣는 수강생. 강사 화면이 수강신청 id 대신 이름을 보여 주기 위해 쓴다. */
public record AttendanceLearner(UUID enrollmentId, UUID userId, String name) {
}
