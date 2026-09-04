package com.adn.dabaeum.support.domain;

import java.util.List;

public interface CommonCodeRepository {

    List<CommonCode> findByGroup(String codeGroup);
}
