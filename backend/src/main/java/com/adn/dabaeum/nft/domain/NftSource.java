package com.adn.dabaeum.nft.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * NFT 메타데이터를 만들 원천. 개인정보(학습자 이름·이메일 등)는 담지 않는다.
 *
 * @param title        배지 이름(단계 배지는 단계 이름 포함) 또는 강좌명
 * @param issuerName   발급 기관명. 플랫폼 배지는 null
 * @param imageFileId  공개 이미지 파일. 없으면 null
 * @param contentHash  수료증 VC 해시. 배지는 null
 */
public record NftSource(
    NftTargetType targetType,
    UUID targetId,
    Instant issuedAt,
    String title,
    String description,
    String issuerName,
    UUID imageFileId,
    String contentHash
) {

    public NftSource {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
    }
}
