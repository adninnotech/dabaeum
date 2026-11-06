package com.adn.dabaeum.attendance.application;

/** 학생 한 명의 다대구 출석 진행 상태. 학생 폰이 이 값을 보고 화면을 바꾼다. */
public enum AttendState {

    /** 다대구 앱에서 인증하기를 기다리는 중. */
    WAITING,

    /** 출결이 기록됐다. */
    RECORDED,

    /** 인증은 됐지만 기록에 실패했다(중복·미수강 등). errorCode 로 이유를 알린다. */
    FAILED,

    /** 제한 시간 안에 인증하지 않았다. */
    EXPIRED
}
