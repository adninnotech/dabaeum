package com.adn.dabaeum.cms.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CmsRepository {

    void savePage(CmsRecords.ContentPage page);

    Optional<CmsRecords.ContentPage> findPage(UUID id);

    Optional<CmsRecords.ContentPage> findPublishedPage(String slug, Instant now);

    /** 최근 수정순. 본문은 목록에서 비운다. */
    List<CmsRecords.ContentPage> findPages();

    boolean updatePage(CmsRecords.ContentPage page);

    boolean deletePage(UUID id);

    boolean isPageReferenced(UUID pageId);

    void saveMenu(CmsRecords.SiteMenu menu);

    Optional<CmsRecords.SiteMenu> findMenu(UUID id);

    /** 부모 먼저(부모 없는 것), 그다음 정렬 순서. */
    List<CmsRecords.MenuWithPage> findMenus();

    boolean updateMenu(CmsRecords.SiteMenu menu);

    boolean deleteMenu(UUID id);

    boolean hasChildren(UUID menuId);

    void updateMenuOrder(CmsRecords.MenuOrder order, Instant at);
}
