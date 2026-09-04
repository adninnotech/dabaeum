package com.adn.dabaeum.completion.infrastructure.mybatis;

import com.adn.dabaeum.completion.domain.CompletionConfirmedEvent;
import com.adn.dabaeum.completion.domain.CompletionOutboxRepository;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CompletionOutboxMyBatisRepository implements CompletionOutboxRepository {

    private final CompletionOutboxMapper mapper;

    public CompletionOutboxMyBatisRepository(CompletionOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CompletionConfirmedEvent event) {
        Objects.requireNonNull(event, "event");
        String payload = "{\"completionId\":\"" + event.completionId()
            + "\",\"courseId\":\"" + event.courseId()
            + "\",\"enrollmentId\":\"" + event.enrollmentId() + "\"}";
        mapper.insert(event, payload);
    }
}
