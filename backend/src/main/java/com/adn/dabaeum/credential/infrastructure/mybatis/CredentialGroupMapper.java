package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CredentialGroupMapper {

    int insert(CredentialGroupRow row);

    CredentialGroupRow selectById(@Param("groupId") UUID groupId);

    CredentialGroupRow selectByCompletionId(@Param("completionId") UUID completionId);

    CredentialGroupRow selectByCompletionIdForUpdate(@Param("completionId") UUID completionId);
}
