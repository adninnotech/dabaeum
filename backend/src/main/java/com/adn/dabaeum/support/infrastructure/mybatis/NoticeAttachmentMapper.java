package com.adn.dabaeum.support.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NoticeAttachmentMapper {

    int insert(NoticeAttachmentRow row);

    List<NoticeAttachmentRow> selectActiveByNoticeId(@Param("noticeId") UUID noticeId);

    NoticeAttachmentRow selectActiveById(@Param("id") UUID id);

    int softDelete(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);
}
