package com.adn.dabaeum.support.domain;

import java.util.Optional;

public interface TermsContentRepository {

    Optional<TermsContent> findLatestByType(TermsType type);
}
