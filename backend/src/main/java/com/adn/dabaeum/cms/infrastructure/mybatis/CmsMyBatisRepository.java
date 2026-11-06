package com.adn.dabaeum.cms.infrastructure.mybatis;

import com.adn.dabaeum.cms.domain.CmsRecords;
import com.adn.dabaeum.cms.domain.CmsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CmsMyBatisRepository implements CmsRepository {

    private final CmsMapper mapper;

    public CmsMyBatisRepository(CmsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void savePage(CmsRecords.ContentPage page) {
        mapper.insertPage(page);
    }

    @Override
    public Optional<CmsRecords.ContentPage> findPage(UUID id) {
        return Optional.ofNullable(mapper.selectPage(id));
    }

    @Override
    public Optional<CmsRecords.ContentPage> findPublishedPage(String slug, Instant now) {
        return Optional.ofNullable(mapper.selectPublishedPage(slug, now));
    }

    @Override
    public List<CmsRecords.ContentPage> findPages() {
        return mapper.selectPages();
    }

    @Override
    public boolean updatePage(CmsRecords.ContentPage page) {
        return mapper.updatePage(page) == 1;
    }

    @Override
    public boolean deletePage(UUID id) {
        return mapper.deletePage(id) == 1;
    }

    @Override
    public boolean isPageReferenced(UUID pageId) {
        return mapper.existsMenuForPage(pageId);
    }

    @Override
    public void saveMenu(CmsRecords.SiteMenu menu) {
        mapper.insertMenu(menu);
    }

    @Override
    public Optional<CmsRecords.SiteMenu> findMenu(UUID id) {
        return Optional.ofNullable(mapper.selectMenu(id));
    }

    @Override
    public List<CmsRecords.MenuWithPage> findMenus() {
        return mapper.selectMenus().stream().map(row -> new CmsRecords.MenuWithPage(
            new CmsRecords.SiteMenu(row.id(), row.parentId(), row.name(), CmsRecords.LinkType.valueOf(row.linkType()),
                row.routeKey(), row.contentPageId(), row.externalUrl(), row.sortOrder(), row.visible(),
                row.createdAt(), row.updatedAt()),
            row.contentSlug(), "PUBLISHED".equals(row.contentStatus()))).toList();
    }

    @Override
    public boolean updateMenu(CmsRecords.SiteMenu menu) {
        return mapper.updateMenu(menu) == 1;
    }

    @Override
    public boolean deleteMenu(UUID id) {
        return mapper.deleteMenu(id) == 1;
    }

    @Override
    public boolean hasChildren(UUID menuId) {
        return mapper.existsChild(menuId);
    }

    @Override
    public void updateMenuOrder(CmsRecords.MenuOrder order, Instant at) {
        mapper.updateMenuOrder(order.id(), order.parentId(), order.sortOrder(), at);
    }
}
