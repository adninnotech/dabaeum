package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstitutionSignKeyVersionMapper {

    int insert(InstitutionSignKeyVersionRow row);

    InstitutionSignKeyVersionRow selectById(@Param("id") UUID id);

    InstitutionSignKeyVersionRow selectBySignKeyIdAndVersion(
        @Param("signKeyId") UUID signKeyId,
        @Param("keyVersion") int keyVersion
    );

    InstitutionSignKeyVersionRow selectBySignKeyIdAndVersionForUpdate(@Param("signKeyId") UUID signKeyId,
        @Param("keyVersion") int keyVersion);

    InstitutionSignKeyVersionRow selectByInstitutionIdAndKidFragment(
        @Param("institutionId") UUID institutionId,
        @Param("kidFragment") String kidFragment
    );

    List<InstitutionSignKeyVersionRow> selectBySignKeyId(@Param("signKeyId") UUID signKeyId);

    List<InstitutionSignKeyVersionRow> selectByInstitutionId(@Param("institutionId") UUID institutionId);

    int retireIfActive(@Param("id") UUID id, @Param("retiredAt") Instant retiredAt);

    int compromise(@Param("id") UUID id, @Param("compromisedAt") Instant compromisedAt);

    String selectStatusForShare(@Param("signKeyId") UUID signKeyId, @Param("keyVersion") int keyVersion);
}
