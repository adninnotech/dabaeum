package com.adn.dabaeum.inquiry.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import java.util.UUID;

public interface InquiryApplicationService {

    Inquiry create(CreateInquiryCommand command);

    Inquiry get(UUID inquiryId, AuthenticatedUserContext actor);

    Inquiry reply(ReplyInquiryCommand command);

    InquiryViewPage listMine(
        AuthenticatedUserContext actor, InquiryStatus status, String keyword,
        int page, int size);

    /**
     * userId 를 그대로 받는 일반화 버전. B-4 관리자 화면이 남의 문의를 보는 데 쓴다.
     * 인가는 하지 않는다 — 호출 권한은 {@code ApiAuthorizationRules} 의 규칙표가 정한다.
     */
    InquiryViewPage listOf(
        UUID userId, InquiryStatus status, String keyword, int page, int size);

    InquiryViewPage listForInstructor(
        AuthenticatedUserContext actor, InquiryStatus status, UUID courseId,
        int page, int size);

    /**
     * userId 를 그대로 받는 일반화 버전. B-4/관리자 강사 상세 화면이 남의 강사 문의를
     * 보는 데 쓴다. 인가는 하지 않는다 — 호출 권한은 {@code ApiAuthorizationRules} 의 규칙표가
     * 정한다.
     */
    InquiryViewPage listForInstructorOf(
        UUID userId, InquiryStatus status, UUID courseId, int page, int size);

    InquiryViewPage listForInstitution(
        AuthenticatedUserContext actor, UUID institutionId, InquiryStatus status,
        UUID courseId, int page, int size);

    /** C-6 문의 유형·강좌별 문의 목록. E-2 학습자 Q&A 도 같은 조회를 쓴다. */
    CourseInquiryViewPage listForCourse(
        AuthenticatedUserContext actor, UUID courseId, InquiryType type, int page, int size);

    /**
     * B-7 플랫폼 관리자 사이트 전체 문의 목록. listForInstitution 과 달리 인가를 하지 않는다 —
     * 호출 권한은 {@code ApiAuthorizationRules} 의 규칙표(/api/v1/admin/* 는 PLATFORM_ADMIN
     * 전용)가 정한다.
     */
    InquiryViewPage listAll(
        InquiryStatus status, InquiryType inquiryType, CourseEducationType educationType,
        UUID institutionId, UUID courseId, String keyword, int page, int size);
}
