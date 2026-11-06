package com.adn.dabaeum.accesslog.web;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자가 이 핸들러로 다른 사람의 개인정보를 다루면 접속기록을 남긴다 (설계서 §10.2).
 * 경로 변수 userId 는 항상 대상이다. 응답 data(단건이면 그 레코드, 목록이면 각 행)의 targetField 값도 대상에 더한다.
 * targetField 가 비어 있으면 행 수만 센다. {@code PersonalDataAccessCoverageTest} 가 붙어야 할 곳을 검사한다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersonalDataAccess {

    AccessLogAction action();

    String targetField() default "";
}
