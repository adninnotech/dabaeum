package com.adn.dabaeum.cms.api;

import com.adn.dabaeum.cms.application.CmsService;
import com.adn.dabaeum.cms.domain.CmsRecords;
import com.adn.dabaeum.cms.domain.CmsRecords.ContentPage;
import com.adn.dabaeum.cms.domain.CmsRecords.LinkType;
import com.adn.dabaeum.cms.domain.MenuRoute;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CMS 요청·응답 형식. 계약은 docs/api/dabaeum-api-v1.yaml 의 Cms* 스키마. */
final class CmsDtos {

    private CmsDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record PageRequest(String slug, String title, String bodyHtml) {

        CmsService.PageCommand toCommand() {
            return new CmsService.PageCommand(slug, title, bodyHtml);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record MenuRequest(UUID parentId, String name, LinkType linkType, String routeKey, UUID contentPageId,
        String externalUrl, Integer sortOrder, Boolean visible) {

        CmsService.MenuCommand toCommand() {
            return new CmsService.MenuCommand(parentId, name, linkType, routeKey, contentPageId, externalUrl, sortOrder,
                visible);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record MenuOrderItem(UUID id, UUID parentId, Integer sortOrder) {

        CmsRecords.MenuOrder toOrder() {
            return new CmsRecords.MenuOrder(id, parentId, sortOrder == null ? 0 : sortOrder);
        }
    }

    record PageSummary(UUID id, String slug, String title, String status, Instant publishedAt, Instant updatedAt) {

        static PageSummary from(ContentPage page) {
            return new PageSummary(page.id(), page.slug(), page.title(), page.status().name(), page.publishedAt(),
                page.updatedAt());
        }
    }

    record PageDetail(UUID id, String slug, String title, String bodyHtml, String status, Instant publishedAt,
        Instant createdAt, Instant updatedAt) {

        static PageDetail from(ContentPage page) {
            return new PageDetail(page.id(), page.slug(), page.title(), page.bodyHtml(), page.status().name(),
                page.publishedAt(), page.createdAt(), page.updatedAt());
        }
    }

    record PublicPage(String slug, String title, String bodyHtml, Instant publishedAt, Instant updatedAt) {

        static PublicPage from(ContentPage page) {
            return new PublicPage(page.slug(), page.title(), page.bodyHtml(), page.publishedAt(), page.updatedAt());
        }
    }

    record AdminMenuNode(UUID id, UUID parentId, String name, String linkType, String routeKey, UUID contentPageId,
        String contentSlug, boolean contentPublished, String externalUrl, int sortOrder, boolean visible,
        List<AdminMenuNode> children) {

        static AdminMenuNode from(CmsService.MenuNode node) {
            CmsRecords.SiteMenu menu = node.menu();
            return new AdminMenuNode(menu.id(), menu.parentId(), menu.name(), menu.linkType().name(), menu.routeKey(),
                menu.contentPageId(), node.contentSlug(), node.contentPublished(), menu.externalUrl(),
                menu.sortOrder(), menu.visible(), node.children().stream().map(AdminMenuNode::from).toList());
        }
    }

    record AdminMenu(UUID id, UUID parentId, String name, String linkType, String routeKey, UUID contentPageId,
        String externalUrl, int sortOrder, boolean visible) {

        static AdminMenu from(CmsRecords.SiteMenu menu) {
            return new AdminMenu(menu.id(), menu.parentId(), menu.name(), menu.linkType().name(), menu.routeKey(),
                menu.contentPageId(), menu.externalUrl(), menu.sortOrder(), menu.visible());
        }
    }

    record PublicMenuNode(UUID id, String name, String linkType, String routeKey, String contentSlug,
        String externalUrl, List<PublicMenuNode> children) {

        static PublicMenuNode from(CmsService.MenuNode node) {
            CmsRecords.SiteMenu menu = node.menu();
            return new PublicMenuNode(menu.id(), menu.name(), menu.linkType().name(), menu.routeKey(),
                node.contentSlug(), menu.externalUrl(),
                node.children().stream().map(PublicMenuNode::from).toList());
        }
    }

    record RouteItem(String key) {

        static RouteItem from(MenuRoute route) {
            return new RouteItem(route.name());
        }
    }
}
