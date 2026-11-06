package com.adn.dabaeum.cms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.cms.domain.CmsRecords.ContentPage;
import com.adn.dabaeum.cms.domain.CmsRecords.LinkType;
import com.adn.dabaeum.cms.domain.CmsRecords.MenuOrder;
import com.adn.dabaeum.cms.domain.CmsRecords.MenuWithPage;
import com.adn.dabaeum.cms.domain.CmsRecords.PageStatus;
import com.adn.dabaeum.cms.domain.CmsRecords.SiteMenu;
import com.adn.dabaeum.cms.domain.CmsRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/** 설계서 §7 — slug·본문 정제, 게시, 2단계 메뉴, 공개 트리 필터. */
@ExtendWith(MockitoExtension.class)
class CmsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    @Mock CmsRepository repository;
    private CmsService service;

    @BeforeEach
    void setUp() {
        service = new CmsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPageStartsAsDraftAndSanitizesBody() {
        ContentPage page = service.createPage(ADMIN,
            new CmsService.PageCommand("about-us", " 소개 ", "<p>안녕</p><script>alert(1)</script>"));

        assertThat(page.status()).isEqualTo(PageStatus.DRAFT);
        assertThat(page.publishedAt()).isNull();
        assertThat(page.title()).isEqualTo("소개");
        assertThat(page.bodyHtml()).contains("<p>안녕</p>").doesNotContain("script");
        verify(repository).savePage(page);
    }

    @Test
    void invalidSlugIsRejected() {
        for (String slug : new String[] {"A", "About", "a b", "한글", null}) {
            assertThatThrownBy(() -> service.createPage(ADMIN, new CmsService.PageCommand(slug, "t", "")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        }
        verify(repository, never()).savePage(any());
    }

    @Test
    void duplicateSlugIsConflict() {
        org.mockito.Mockito.doThrow(new DuplicateKeyException("dup")).when(repository).savePage(any());

        assertThatThrownBy(() -> service.createPage(ADMIN, new CmsService.PageCommand("about", "t", "")))
            .extracting("code").isEqualTo(ApiErrorCode.CONTENT_PAGE_CONFLICT);
    }

    @Test
    void publishKeepsFirstPublishedAtAndUnpublishClearsIt() {
        ContentPage published = page(PageStatus.PUBLISHED, NOW.minusSeconds(3600));
        when(repository.findPage(published.id())).thenReturn(Optional.of(published));

        assertThat(service.publish(ADMIN, published.id(), true).publishedAt()).isEqualTo(NOW.minusSeconds(3600));
        ContentPage draft = service.publish(ADMIN, published.id(), false);
        assertThat(draft.status()).isEqualTo(PageStatus.DRAFT);
        assertThat(draft.publishedAt()).isNull();
    }

    @Test
    void referencedPageCannotBeDeleted() {
        ContentPage page = page(PageStatus.DRAFT, null);
        when(repository.findPage(page.id())).thenReturn(Optional.of(page));
        when(repository.isPageReferenced(page.id())).thenReturn(true);

        assertThatThrownBy(() -> service.deletePage(page.id()))
            .extracting("code").isEqualTo(ApiErrorCode.CONTENT_PAGE_IN_USE);
        verify(repository, never()).deletePage(any());
    }

    @Test
    void menuTargetMustMatchLinkType() {
        assertThatThrownBy(() -> service.createMenu(command(null, LinkType.ROUTE, "NOPE", null, null)))
            .extracting("details").isEqualTo(List.of("routeKey"));
        assertThatThrownBy(() -> service.createMenu(command(null, LinkType.EXTERNAL, null, null, "javascript:alert(1)")))
            .extracting("details").isEqualTo(List.of("externalUrl"));
        assertThatThrownBy(() -> service.createMenu(command(null, LinkType.CONTENT, null, UUID.randomUUID(), null)))
            .extracting("details").isEqualTo(List.of("contentPageId"));

        SiteMenu created = service.createMenu(command(null, LinkType.EXTERNAL, "HOME", null, "https://daegu.go.kr"));
        assertThat(created.routeKey()).isNull();
        assertThat(created.externalUrl()).isEqualTo("https://daegu.go.kr");
        assertThat(created.visible()).isTrue();
    }

    @Test
    void menusAreLimitedToTwoLevels() {
        SiteMenu top = menu(null, true, LinkType.ROUTE);
        SiteMenu child = menu(top.id(), true, LinkType.ROUTE);
        when(repository.findMenu(child.id())).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.createMenu(command(child.id(), LinkType.ROUTE, "HOME", null, null)))
            .extracting("details").isEqualTo(List.of("parentId"));
        verify(repository, never()).saveMenu(any());
    }

    @Test
    void menuWithChildrenCannotBeDeleted() {
        SiteMenu top = menu(null, true, LinkType.ROUTE);
        when(repository.findMenu(top.id())).thenReturn(Optional.of(top));
        when(repository.hasChildren(top.id())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteMenu(top.id()))
            .extracting("code").isEqualTo(ApiErrorCode.SITE_MENU_HAS_CHILDREN);
    }

    @Test
    void publicTreeDropsHiddenBranchesAndUnpublishedContent() {
        SiteMenu shown = menu(null, true, LinkType.ROUTE);
        SiteMenu hidden = menu(null, false, LinkType.ROUTE);
        SiteMenu underHidden = menu(hidden.id(), true, LinkType.ROUTE);
        SiteMenu draftContent = menu(shown.id(), true, LinkType.CONTENT);
        SiteMenu liveContent = menu(shown.id(), true, LinkType.CONTENT);
        when(repository.findMenus()).thenReturn(List.of(
            new MenuWithPage(shown, null, false), new MenuWithPage(hidden, null, false),
            new MenuWithPage(underHidden, null, false), new MenuWithPage(draftContent, "draft", false),
            new MenuWithPage(liveContent, "live", true)));

        List<CmsService.MenuNode> tree = service.publicMenuTree();

        assertThat(tree).extracting(node -> node.menu().id()).containsExactly(shown.id());
        assertThat(tree.getFirst().children()).extracting(CmsService.MenuNode::contentSlug).containsExactly("live");
        assertThat(service.menuTree()).hasSize(2);
    }

    @Test
    void reorderRejectsThirdLevelAndUnknownIds() {
        SiteMenu a = menu(null, true, LinkType.ROUTE);
        SiteMenu b = menu(null, true, LinkType.ROUTE);
        SiteMenu c = menu(b.id(), true, LinkType.ROUTE);
        when(repository.findMenus()).thenReturn(List.of(
            new MenuWithPage(a, null, false), new MenuWithPage(b, null, false), new MenuWithPage(c, null, false)));

        assertThatThrownBy(() -> service.reorder(List.of(new MenuOrder(b.id(), a.id(), 0))))
            .extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service.reorder(List.of(new MenuOrder(UUID.randomUUID(), null, 0))))
            .extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(repository, never()).updateMenuOrder(any(), any());

        service.reorder(List.of(new MenuOrder(c.id(), a.id(), 1), new MenuOrder(b.id(), a.id(), 2)));
        ArgumentCaptor<MenuOrder> order = ArgumentCaptor.forClass(MenuOrder.class);
        verify(repository, org.mockito.Mockito.times(2)).updateMenuOrder(order.capture(), any());
        assertThat(order.getAllValues()).extracting(MenuOrder::parentId).containsOnly(a.id());
    }

    private static CmsService.MenuCommand command(UUID parent, LinkType type, String route, UUID page, String url) {
        return new CmsService.MenuCommand(parent, "메뉴", type, route, page, url, null, null);
    }

    private static SiteMenu menu(UUID parent, boolean visible, LinkType type) {
        return new SiteMenu(UUID.randomUUID(), parent, "메뉴", type, type == LinkType.ROUTE ? "HOME" : null,
            type == LinkType.CONTENT ? UUID.randomUUID() : null, null, 0, visible, NOW, NOW);
    }

    private static ContentPage page(PageStatus status, Instant publishedAt) {
        return new ContentPage(UUID.randomUUID(), "about", "소개", "<p>x</p>", status, publishedAt, ADMIN, ADMIN, NOW, NOW);
    }
}
