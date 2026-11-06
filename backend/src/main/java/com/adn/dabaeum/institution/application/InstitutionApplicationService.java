package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionAdministrator;
import java.util.List;
import java.util.UUID;

public interface InstitutionApplicationService {

    /** 기관을 만들고 같은 트랜잭션에서 서명키 생성을 요청한다. requestedBy 는 생성한 관리자다. */
    Institution create(CreateInstitutionCommand command, UUID requestedBy);

    Institution get(UUID institutionId);

    InstitutionPage list(ListInstitutionsQuery query);

    Institution update(UpdateInstitutionCommand command);

    /** 기관 상세 전용. tb_user_roles 의 INSTITUTION_ADMIN 을 역방향으로 조회한다. */
    List<InstitutionAdministrator> administrators(UUID institutionId);
}
