package com.adn.dabaeum.identity.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserIdentityMapper {

    int insert(UserIdentityRow row);

    UserIdentityRow selectById(@Param("id") UUID id);

    UserIdentityRow selectByProviderSubject(
        @Param("provider") String provider,
        @Param("providerSubject") String providerSubject
    );

    List<UserIdentityRow> selectByUserId(@Param("userId") UUID userId);

    int deleteByIdAndUserId(
        @Param("identityId") UUID identityId,
        @Param("userId") UUID userId
    );
}
