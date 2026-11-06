package com.adn.dabaeum.support.infrastructure.mybatis;

import java.time.Instant;
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
        @Param("offset") int offset,
        @Param("now") Instant now
    );

    long countPublishedNotices(@Param("audience") String audience, @Param("now") Instant now);

    List<NoticeRow> selectAdminNoticesFiltered(
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("keyword") String keyword,
        @Param("audience") String audience,
        @Param("status") String status
    );

    long countAllNoticesFiltered(
        @Param("keyword") String keyword,
        @Param("audience") String audience,
        @Param("status") String status
    );

    int updateNotice(NoticeRow row);

    int deleteNotice(@Param("id") UUID id);

    List<FaqRow> selectFaqs(
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countFaqs();

    int insertFaq(FaqRow row);

    FaqRow selectFaqById(@Param("id") UUID id);

    int updateFaq(FaqRow row);

    int deleteFaq(@Param("id") UUID id);

    TermsContentRow selectLatestTermsByType(@Param("type") String type);

    int insertTermsContent(TermsContentRow row);

    List<CommonCodeRow> selectCodesByGroup(@Param("codeGroup") String codeGroup);
}
