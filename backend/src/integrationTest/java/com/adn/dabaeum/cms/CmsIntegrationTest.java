package com.adn.dabaeum.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.cms.application.CmsService;
import com.adn.dabaeum.cms.domain.CmsRecords.ContentPage;
import com.adn.dabaeum.cms.domain.CmsRecords.LinkType;
import com.adn.dabaeum.cms.domain.CmsRecords.MenuOrder;
import com.adn.dabaeum.cms.domain.CmsRecords.SiteMenu;
import com.adn.dabaeum.cms.domain.CmsRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §7 — 콘텐츠 페이지·메뉴 저장, 공개 트리, 참조 보호, DB CHECK. */
class CmsIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired CmsService service;
    @Autowired CmsRepository repository;

    @Test
    void pageAndMenuLifecycle() {
        UUID admin = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name) VALUES (?, ?)", admin, "CMS 관리자");
        String slug = "it-" + UUID.randomUUID().toString().substring(0, 8);

        ContentPage page = service.createPage(admin, new CmsService.PageCommand(slug, "소개", "<p>본문</p>"));
        assertThatThrownBy(() -> service.publishedPage(slug)).isInstanceOf(ApiException.class);
        assertThat(service.pages()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(page.id());
            assertThat(item.bodyHtml()).isEmpty();
        });

        SiteMenu top = service.createMenu(menu(null, "안내", LinkType.ROUTE, "HOME", null, null));
        SiteMenu content = service.createMenu(menu(top.id(), "소개", LinkType.CONTENT, null, page.id(), null));
        SiteMenu external = service.createMenu(menu(top.id(), "대구시", LinkType.EXTERNAL, null, null, "https://www.daegu.go.kr"));

        assertThat(publicChildren(top.id())).containsExactly("대구시");
        service.publish(admin, page.id(), true);
        assertThat(service.publishedPage(slug).bodyHtml()).contains("본문");
        assertThat(publicChildren(top.id())).containsExactlyInAnyOrder("소개", "대구시");

        service.reorder(List.of(new MenuOrder(external.id(), top.id(), 0), new MenuOrder(content.id(), top.id(), 1)));
        assertThat(publicChildren(top.id())).containsExactly("대구시", "소개");

        assertThatThrownBy(() -> service.deletePage(page.id()))
            .extracting("code").isEqualTo(ApiErrorCode.CONTENT_PAGE_IN_USE);
        assertThatThrownBy(() -> service.deleteMenu(top.id()))
            .extracting("code").isEqualTo(ApiErrorCode.SITE_MENU_HAS_CHILDREN);

        service.deleteMenu(content.id());
        service.deletePage(page.id());
        assertThat(repository.findPage(page.id())).isEmpty();
    }

    /** 유일성 위반은 트랜잭션을 중단시키므로 마지막 문장이어야 한다. */
    @Test
    void duplicateSlugIsConflict() {
        UUID admin = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name) VALUES (?, ?)", admin, "CMS 관리자");
        String slug = "it-" + UUID.randomUUID().toString().substring(0, 8);
        service.createPage(admin, new CmsService.PageCommand(slug, "소개", ""));

        assertThatThrownBy(() -> service.createPage(admin, new CmsService.PageCommand(slug, "중복", "")))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.CONTENT_PAGE_CONFLICT);
    }

    @Test
    void databaseRejectsMenuWithMismatchedTarget() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO tb_site_menus (id, name, link_type, route_key, external_url)
            VALUES (?, '잘못', 'ROUTE', 'HOME', 'https://x.kr')
            """, UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private List<String> publicChildren(UUID parent) {
        return service.publicMenuTree().stream()
            .filter(node -> node.menu().id().equals(parent))
            .flatMap(node -> node.children().stream())
            .map(node -> node.menu().name())
            .toList();
    }

    private static CmsService.MenuCommand menu(UUID parent, String name, LinkType type, String route, UUID page,
        String url) {
        return new CmsService.MenuCommand(parent, name, type, route, page, url, null, true);
    }
}
