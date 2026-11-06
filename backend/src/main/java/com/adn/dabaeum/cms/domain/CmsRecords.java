package com.adn.dabaeum.cms.domain;

import java.time.Instant;
import java.util.UUID;

/** CMS 레코드 (설계서 §7.1). */
public final class CmsRecords {

    private CmsRecords() {
    }

    public enum PageStatus { DRAFT, PUBLISHED }

    public enum LinkType { ROUTE, CONTENT, EXTERNAL }

    public record ContentPage(UUID id, String slug, String title, String bodyHtml, PageStatus status,
        Instant publishedAt, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {
    }

    public record SiteMenu(UUID id, UUID parentId, String name, LinkType linkType, String routeKey,
        UUID contentPageId, String externalUrl, int sortOrder, boolean visible, Instant createdAt, Instant updatedAt) {
    }

    /** 메뉴 트리용: 메뉴와 (CONTENT 면) 연결 페이지의 슬러그·게시 여부. */
    public record MenuWithPage(SiteMenu menu, String contentSlug, boolean contentPublished) {
    }

    public record MenuOrder(UUID id, UUID parentId, int sortOrder) {
    }
}
