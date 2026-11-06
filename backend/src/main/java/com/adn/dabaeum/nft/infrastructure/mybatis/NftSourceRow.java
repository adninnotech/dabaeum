package com.adn.dabaeum.nft.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record NftSourceRow(UUID targetId, Instant issuedAt, String title, String description,
    String issuerName, UUID imageFileId, String contentHash) { }
