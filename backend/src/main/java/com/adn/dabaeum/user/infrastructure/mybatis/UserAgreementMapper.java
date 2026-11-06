package com.adn.dabaeum.user.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAgreementMapper {

    int insert(UserAgreementRow row);

    List<UserAgreementRow> selectByUserId(@Param("userId") UUID userId);
}
