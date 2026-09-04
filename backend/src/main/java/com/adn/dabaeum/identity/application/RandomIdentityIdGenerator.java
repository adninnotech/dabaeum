package com.adn.dabaeum.identity.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomIdentityIdGenerator implements IdentityIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
