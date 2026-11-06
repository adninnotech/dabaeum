package com.adn.dabaeum.support.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.domain.CommonCode;
import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.TermsType;
import java.util.List;
import java.util.UUID;

public interface SupportApplicationService {

    NoticePage listPublishedNotices(NoticeAudience audience, int page, int size);

    Notice getPublishedNotice(UUID noticeId);

    /** 관리자 수정 화면용. 초안·예약 공지도 돌려준다. */
    Notice getAdminNotice(UUID noticeId, AuthenticatedUserContext actor);

    NoticePage listAdminNotices(
        AuthenticatedUserContext actor, int page, int size,
        String keyword, NoticeAudience audience, com.adn.dabaeum.support.domain.NoticeStatus status);

    Notice createNotice(NoticeCommand command);

    Notice updateNotice(NoticeCommand command);

    void deleteNotice(UUID noticeId, AuthenticatedUserContext actor);

    FaqPage listFaqs(int page, int size);

    FaqPage listAdminFaqs(AuthenticatedUserContext actor, int page, int size);

    Faq createFaq(FaqCommand command);

    Faq updateFaq(FaqCommand command);

    void deleteFaq(UUID faqId, AuthenticatedUserContext actor);

    TermsContent getTerms(TermsType type);

    TermsContent createTerms(TermsContentCommand command);

    List<CommonCode> listCodes(String codeGroup);
}
