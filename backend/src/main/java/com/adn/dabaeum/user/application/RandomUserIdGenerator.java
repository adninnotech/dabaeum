package com.adn.dabaeum.user.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomUserIdGenerator implements UserIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
