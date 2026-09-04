package com.adn.dabaeum.role.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomRoleIdGenerator implements RoleIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
