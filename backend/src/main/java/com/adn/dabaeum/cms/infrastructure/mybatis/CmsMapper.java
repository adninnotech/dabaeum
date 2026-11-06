package com.adn.dabaeum.cms.infrastructure.mybatis;

import com.adn.dabaeum.cms.domain.CmsRecords;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CmsMapper {

    int insertPage(CmsRecords.ContentPage page);

    CmsRecords.ContentPage selectPage(@Param("id") UUID id);

    CmsRecords.ContentPage selectPublishedPage(@Param("slug") String slug, @Param("now") Instant now);

    List<CmsRecords.ContentPage> selectPages();

    int updatePage(CmsRecords.ContentPage page);

    int deletePage(@Param("id") UUID id);

    boolean existsMenuForPage(@Param("pageId") UUID pageId);

    int insertMenu(CmsRecords.SiteMenu menu);

    CmsRecords.SiteMenu selectMenu(@Param("id") UUID id);

    List<MenuRow> selectMenus();

    int updateMenu(CmsRecords.SiteMenu menu);

    int deleteMenu(@Param("id") UUID id);

    boolean existsChild(@Param("id") UUID id);

    int updateMenuOrder(@Param("id") UUID id, @Param("parentId") UUID parentId, @Param("sortOrder") int sortOrder,
        @Param("at") Instant at);

    /** 메뉴 + 연결 페이지 슬러그·상태를 한 행으로. */
    record MenuRow(UUID id, UUID parentId, String name, String linkType, String routeKey, UUID contentPageId,
        String externalUrl, int sortOrder, boolean visible, Instant createdAt, Instant updatedAt,
        String contentSlug, String contentStatus) {
    }
}
