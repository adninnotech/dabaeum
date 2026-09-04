package com.adn.dabaeum.credential.application;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class RandomCredentialGroupIdGenerator implements CredentialGroupIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
