package com.adn.dabaeum.inquiry.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import java.util.UUID;

public interface InquiryApplicationService {

    Inquiry create(CreateInquiryCommand command);

    Inquiry get(UUID inquiryId, AuthenticatedUserContext actor);

    Inquiry reply(ReplyInquiryCommand command);

    InquiryViewPage listMine(
        AuthenticatedUserContext actor, InquiryStatus status, int page, int size);

    InquiryViewPage listForInstructor(
        AuthenticatedUserContext actor, InquiryStatus status, UUID courseId,
        int page, int size);

    InquiryViewPage listForInstitution(
        AuthenticatedUserContext actor, UUID institutionId, InquiryStatus status,
        UUID courseId, int page, int size);
}
