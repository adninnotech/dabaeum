package com.adn.dabaeum.support.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SupportMapper {

    int insertNotice(NoticeRow row);

    NoticeRow selectNoticeById(@Param("id") UUID id);

    List<NoticeRow> selectPublishedNotices(
        @Param("audience") String audience,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countPublishedNotices(@Param("audience") String audience);

    List<NoticeRow> selectAdminNotices(
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countAllNotices();

    int updateNotice(NoticeRow row);

    int deleteNotice(@Param("id") UUID id);

    List<FaqRow> selectFaqs(
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countFaqs();

    TermsContentRow selectLatestTermsByType(@Param("type") String type);

    List<CommonCodeRow> selectCodesByGroup(@Param("codeGroup") String codeGroup);
}
