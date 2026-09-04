package com.adn.dabaeum.completion.domain;

public interface CompletionOutboxRepository {

    void save(CompletionConfirmedEvent event);
}
