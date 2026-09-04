package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.Institution;
import java.util.UUID;

public interface InstitutionApplicationService {

    Institution create(CreateInstitutionCommand command);

    Institution get(UUID institutionId);

    InstitutionPage list(ListInstitutionsQuery query);

    Institution update(UpdateInstitutionCommand command);
}
