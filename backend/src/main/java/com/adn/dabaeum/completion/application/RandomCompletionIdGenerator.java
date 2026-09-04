package com.adn.dabaeum.completion.application;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class RandomCompletionIdGenerator implements CompletionIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
