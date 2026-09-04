package com.adn.dabaeum.support.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.domain.CommonCode;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.TermsType;
import java.util.List;
import java.util.UUID;

public interface SupportApplicationService {

    NoticePage listPublishedNotices(NoticeAudience audience, int page, int size);

    Notice getPublishedNotice(UUID noticeId);

    NoticePage listAdminNotices(AuthenticatedUserContext actor, int page, int size);

    Notice createNotice(NoticeCommand command);

    Notice updateNotice(NoticeCommand command);

    void deleteNotice(UUID noticeId, AuthenticatedUserContext actor);

    FaqPage listFaqs(int page, int size);

    TermsContent getTerms(TermsType type);

    List<CommonCode> listCodes(String codeGroup);
}
