package com.adn.dabaeum.banner.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BannerMapper {

    int insert(BannerRow row);

    BannerRow selectById(@Param("id") UUID id);

    List<BannerRow> selectAll();

    int update(BannerRow row);

    int softDelete(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);
}
