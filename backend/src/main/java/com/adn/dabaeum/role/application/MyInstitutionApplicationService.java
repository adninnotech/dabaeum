package com.adn.dabaeum.role.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.List;

public interface MyInstitutionApplicationService {

    List<MyInstitution> listMine(AuthenticatedUserContext context);
}
