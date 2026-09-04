package com.adn.dabaeum.credential.application;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class RandomCredentialIdGenerator implements CredentialIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
