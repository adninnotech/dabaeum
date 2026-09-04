package com.adn.dabaeum.institution.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomInstitutionIdGenerator implements InstitutionIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
