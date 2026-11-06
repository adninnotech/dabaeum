package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LegacySignKeyBackfillMapper {

    /** UUID 는 MyBatis 기본 TypeHandler 가 없어 text 로 받는다. 변환은 리포지토리가 한다. */
    List<String> selectInstitutionsWithUnlinkedRows();

    int linkCredentials(@Param("institutionId") UUID institutionId, @Param("signKeyId") UUID signKeyId);

    int linkStatusLists(@Param("institutionId") UUID institutionId, @Param("signKeyId") UUID signKeyId);
}
