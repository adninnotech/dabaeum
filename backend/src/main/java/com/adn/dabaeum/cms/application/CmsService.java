package com.adn.dabaeum.cms.application;

import com.adn.dabaeum.cms.domain.CmsRecords;
import com.adn.dabaeum.cms.domain.CmsRecords.ContentPage;
import com.adn.dabaeum.cms.domain.CmsRecords.LinkType;
import com.adn.dabaeum.cms.domain.CmsRecords.MenuWithPage;
import com.adn.dabaeum.cms.domain.CmsRecords.PageStatus;
import com.adn.dabaeum.cms.domain.CmsRecords.SiteMenu;
import com.adn.dabaeum.cms.domain.CmsRepository;
import com.adn.dabaeum.cms.domain.MenuRoute;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.text.RichText;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CMS 메뉴·콘텐츠 (설계서 §7). 메뉴는 2단계까지, 본문 HTML 은 저장 전에 정제한다. */
@Service
@Profile({"local", "dev"})
public class CmsService {

    private static final String SLUG_PATTERN = "^[a-z0-9-]{2,100}$";
    private static final int MAX_TITLE = 200;
    private static final int MAX_MENU_NAME = 60;

    private final CmsRepository repository;
    private final Clock clock;

    public CmsService(CmsRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public record PageCommand(String slug, String title, String bodyHtml) {
    }

    public record MenuCommand(UUID parentId, String name, LinkType linkType, String routeKey, UUID contentPageId,
        String externalUrl, Integer sortOrder, Boolean visible) {
    }

    public record MenuNode(SiteMenu menu, String contentSlug, boolean contentPublished, List<MenuNode> children) {

        public MenuNode {
            children = List.copyOf(children);
        }
    }

    // ---- 콘텐츠 페이지 ----

    @Transactional(readOnly = true)
    public List<ContentPage> pages() {
        return repository.findPages();
    }

    @Transactional(readOnly = true)
    public ContentPage page(UUID id) {
        return repository.findPage(id).orElseThrow(CmsService::pageNotFound);
    }

    @Transactional(readOnly = true)
    public ContentPage publishedPage(String slug) {
        return repository.findPublishedPage(slug, clock.instant()).orElseThrow(CmsService::pageNotFound);
    }

    @Transactional
    public ContentPage createPage(UUID actor, PageCommand command) {
        Instant now = clock.instant();
        ContentPage page = new ContentPage(UUID.randomUUID(), slug(command.slug()), title(command.title()),
            body(command.bodyHtml()), PageStatus.DRAFT, null, actor, actor, now, now);
        try {
            repository.savePage(page);
        } catch (DataIntegrityViolationException exception) {
            throw slugConflict();
        }
        return page;
    }

    @Transactional
    public ContentPage updatePage(UUID actor, UUID id, PageCommand command) {
        ContentPage existing = page(id);
        ContentPage updated = new ContentPage(id, slug(command.slug()), title(command.title()), body(command.bodyHtml()),
            existing.status(), existing.publishedAt(), existing.createdBy(), actor, existing.createdAt(), clock.instant());
        try {
            repository.updatePage(updated);
        } catch (DataIntegrityViolationException exception) {
            throw slugConflict();
        }
        return updated;
    }

    @Transactional
    public ContentPage publish(UUID actor, UUID id, boolean published) {
        ContentPage existing = page(id);
        Instant now = clock.instant();
        ContentPage updated = new ContentPage(id, existing.slug(), existing.title(), existing.bodyHtml(),
            published ? PageStatus.PUBLISHED : PageStatus.DRAFT,
            published ? (existing.publishedAt() == null ? now : existing.publishedAt()) : null,
            existing.createdBy(), actor, existing.createdAt(), now);
        repository.updatePage(updated);
        return updated;
    }

    @Transactional
    public void deletePage(UUID id) {
        page(id);
        if (repository.isPageReferenced(id)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.CONTENT_PAGE_IN_USE,
                "Content page is linked from a menu");
        }
        repository.deletePage(id);
    }

    // ---- 메뉴 ----

    /** 관리용 전체 트리(숨김 포함). */
    @Transactional(readOnly = true)
    public List<MenuNode> menuTree() {
        return tree(repository.findMenus(), false);
    }

    /** 공개 트리: 보이는 것만, 부모가 숨겨지면 자식도, CONTENT 는 페이지가 게시됐을 때만. */
    @Transactional(readOnly = true)
    public List<MenuNode> publicMenuTree() {
        return tree(repository.findMenus(), true);
    }

    @Transactional
    public SiteMenu createMenu(MenuCommand command) {
        Instant now = clock.instant();
        SiteMenu menu = menu(UUID.randomUUID(), command, now, now);
        requireParentAllowed(menu.id(), menu.parentId());
        repository.saveMenu(menu);
        return menu;
    }

    @Transactional
    public SiteMenu updateMenu(UUID id, MenuCommand command) {
        SiteMenu existing = repository.findMenu(id).orElseThrow(CmsService::menuNotFound);
        SiteMenu updated = menu(id, command, existing.createdAt(), clock.instant());
        requireParentAllowed(id, updated.parentId());
        repository.updateMenu(updated);
        return updated;
    }

    @Transactional
    public void deleteMenu(UUID id) {
        repository.findMenu(id).orElseThrow(CmsService::menuNotFound);
        if (repository.hasChildren(id)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.SITE_MENU_HAS_CHILDREN, "Menu has children");
        }
        repository.deleteMenu(id);
    }

    /** 전체 배치를 한 번에 바꾼다. 결과가 2단계를 넘거나 모르는 메뉴가 있으면 422. */
    @Transactional
    public List<MenuNode> reorder(List<CmsRecords.MenuOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            throw validation("orders");
        }
        Map<UUID, UUID> parents = new HashMap<>();
        repository.findMenus().forEach(item -> parents.put(item.menu().id(), item.menu().parentId()));
        Set<UUID> seen = new HashSet<>();
        for (CmsRecords.MenuOrder order : orders) {
            if (order.id() == null || !parents.containsKey(order.id()) || !seen.add(order.id())) {
                throw validation("orders");
            }
            parents.put(order.id(), order.parentId());
        }
        for (Map.Entry<UUID, UUID> entry : parents.entrySet()) {
            UUID parent = entry.getValue();
            if (parent != null && (!parents.containsKey(parent) || parents.get(parent) != null || parent.equals(entry.getKey()))) {
                throw validation("orders");
            }
        }
        Instant now = clock.instant();
        orders.forEach(order -> repository.updateMenuOrder(order, now));
        return menuTree();
    }

    public List<MenuRoute> routes() {
        return List.of(MenuRoute.values());
    }

    private SiteMenu menu(UUID id, MenuCommand command, Instant createdAt, Instant now) {
        if (command == null || command.linkType() == null) {
            throw validation("linkType");
        }
        String name = command.name() == null ? "" : command.name().trim();
        if (name.isEmpty() || name.length() > MAX_MENU_NAME) {
            throw validation("name");
        }
        String routeKey = null;
        UUID pageId = null;
        String url = null;
        switch (command.linkType()) {
            case ROUTE -> {
                try {
                    routeKey = MenuRoute.valueOf(String.valueOf(command.routeKey())).name();
                } catch (IllegalArgumentException exception) {
                    throw validation("routeKey");
                }
            }
            case CONTENT -> {
                pageId = command.contentPageId();
                if (pageId == null || repository.findPage(pageId).isEmpty()) {
                    throw validation("contentPageId");
                }
            }
            case EXTERNAL -> url = externalUrl(command.externalUrl());
        }
        return new SiteMenu(id, command.parentId(), name, command.linkType(), routeKey, pageId, url,
            command.sortOrder() == null ? 0 : command.sortOrder(), command.visible() == null || command.visible(),
            createdAt, now);
    }

    /** 부모는 최상위 메뉴여야 하고, 자식이 있는 메뉴는 누군가의 자식이 될 수 없다(2단계). */
    private void requireParentAllowed(UUID id, UUID parentId) {
        if (parentId == null) {
            return;
        }
        SiteMenu parent = repository.findMenu(parentId).orElseThrow(() -> validation("parentId"));
        if (parent.parentId() != null || parentId.equals(id) || repository.hasChildren(id)) {
            throw validation("parentId");
        }
    }

    private static List<MenuNode> tree(List<MenuWithPage> all, boolean publicOnly) {
        Map<UUID, List<MenuNode>> childrenByParent = new HashMap<>();
        for (MenuWithPage item : all) {
            if (item.menu().parentId() != null && include(item, publicOnly)) {
                childrenByParent.computeIfAbsent(item.menu().parentId(), key -> new ArrayList<>())
                    .add(new MenuNode(item.menu(), item.contentSlug(), item.contentPublished(), List.of()));
            }
        }
        return all.stream()
            .filter(item -> item.menu().parentId() == null && include(item, publicOnly))
            .map(item -> new MenuNode(item.menu(), item.contentSlug(), item.contentPublished(),
                childrenByParent.getOrDefault(item.menu().id(), List.of())))
            .toList();
    }

    private static boolean include(MenuWithPage item, boolean publicOnly) {
        if (!publicOnly) {
            return true;
        }
        return item.menu().visible() && (item.menu().linkType() != LinkType.CONTENT || item.contentPublished());
    }

    private static String slug(String value) {
        String slug = value == null ? "" : value.trim();
        if (!slug.matches(SLUG_PATTERN)) {
            throw validation("slug");
        }
        return slug;
    }

    private static String title(String value) {
        String title = value == null ? "" : value.trim();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw validation("title");
        }
        return title;
    }

    private static String body(String html) {
        String sanitized = RichText.sanitize(html == null ? "" : html);
        return sanitized == null ? "" : sanitized;
    }

    private static String externalUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (uri.getHost() == null || !Set.of("http", "https").contains(String.valueOf(uri.getScheme()).toLowerCase())
                || value.length() > 500) {
                throw validation("externalUrl");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw validation("externalUrl");
        }
    }

    private static ApiException pageNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.CONTENT_PAGE_NOT_FOUND, "Content page not found");
    }

    private static ApiException menuNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.SITE_MENU_NOT_FOUND, "Menu not found");
    }

    private static ApiException slugConflict() {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.CONTENT_PAGE_CONFLICT, "Slug already exists");
    }

    private static ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
