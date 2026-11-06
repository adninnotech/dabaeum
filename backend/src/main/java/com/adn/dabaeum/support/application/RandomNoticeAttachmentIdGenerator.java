package com.adn.dabaeum.support.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomNoticeAttachmentIdGenerator implements NoticeAttachmentIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
