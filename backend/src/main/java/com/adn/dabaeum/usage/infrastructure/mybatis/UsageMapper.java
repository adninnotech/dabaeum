package com.adn.dabaeum.usage.infrastructure.mybatis;

import com.adn.dabaeum.usage.domain.BannerClickCount;
import com.adn.dabaeum.usage.domain.KeywordCount;
import com.adn.dabaeum.usage.domain.MemberProfile;
import com.adn.dabaeum.usage.domain.SegmentCount;
import com.adn.dabaeum.usage.domain.UsageCounts;
import com.adn.dabaeum.usage.domain.UsageEvent;
import com.adn.dabaeum.usage.domain.UsageTrendRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UsageMapper {

    MemberProfile selectMember(@Param("userId") UUID userId);

    int insertEventIfAbsent(@Param("event") UsageEvent event);

    int deleteEventsBefore(@Param("date") LocalDate date);

    int insertSaltIfAbsent(@Param("date") LocalDate date, @Param("salt") byte[] salt);

    UsageSaltRow selectSalt(@Param("date") LocalDate date);

    int deleteSaltsBefore(@Param("date") LocalDate date);

    LocalDate selectLastAggregatedDay();

    int deleteDailyStats(@Param("day") LocalDate day);

    int insertDailyStats(@Param("day") LocalDate day, @Param("dayStart") Instant dayStart,
        @Param("dayEnd") Instant dayEnd);

    int deleteKeywordStats(@Param("day") LocalDate day);

    int insertKeywordStats(@Param("day") LocalDate day, @Param("minSearchers") int minSearchers,
        @Param("masked") String masked);

    int markAggregated(@Param("day") LocalDate day, @Param("at") Instant at);

    List<UsageTrendRow> selectTrend(@Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("monthly") boolean monthly);

    UsageCounts selectTotals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<SegmentCount> selectSegments(@Param("metric") String metric, @Param("from") LocalDate from,
        @Param("to") LocalDate to);

    List<KeywordCount> selectKeywords(@Param("zeroResult") boolean zeroResult, @Param("from") LocalDate from,
        @Param("to") LocalDate to, @Param("limit") int limit);

    List<BannerClickCount> selectBannerClicks(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
