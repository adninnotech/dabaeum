package com.adn.dabaeum.credential.infrastructure.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * 매퍼 XML 의 동적 SQL 이 파라미터마다 타입 핸들러를 실제로 해석하는지 확인한다.
 *
 * <p>MyBatis 는 {@code <foreach>} 항목을 @Param Map 이 아니라 값의 실제 타입으로 해석하므로,
 * 기본 registry 에 핸들러가 없는 {@link UUID} 를 그대로 쓰면 SQL 실행 이전
 * {@code BoundSql} 생성 단계에서 "Type handler was null" 로 실패한다. 이 실패는 DB 연결 없이
 * 재현되므로 통합 테스트를 기다리지 않고 여기서 막는다.
 */
class CredentialQueryMapperStatementTest {

    private static final String NAMESPACE =
        "com.adn.dabaeum.credential.infrastructure.mybatis.CredentialQueryMapper";
    private static final String RESOURCE =
        "mybatis/mapper/credential/CredentialQueryMapper.xml";

    @Test
    void resolvesTypeHandlersForEveryUuidParameterIncludingForeachItems() throws Exception {
        Configuration configuration = configuration();

        BoundSql single = boundSql(configuration, "selectCourseByCredentialId",
            Map.of("credentialId", UUID.randomUUID()));
        assertThat(single.getSql()).contains("c.id = ?");
        assertHandlersResolved(single);

        Map<String, Object> many = new HashMap<>();
        many.put("credentialIds", List.of(UUID.randomUUID(), UUID.randomUUID()));
        BoundSql batch = boundSql(configuration, "selectCoursesByCredentialIds", many);
        assertThat(batch.getSql()).contains("c.id IN");
        assertThat(batch.getParameterMappings()).hasSize(2);
        assertHandlersResolved(batch);

        Map<String, Object> scoped = new HashMap<>();
        scoped.put("userId", UUID.randomUUID());
        scoped.put("institutionIds", List.of(UUID.randomUUID(), UUID.randomUUID()));
        scoped.put("limit", 20);
        scoped.put("offset", 0);
        scoped.put("sortField", "created_at");
        scoped.put("sortDirection", "DESC");
        BoundSql institutionScoped = boundSql(configuration, "selectByUserIdAndInstitutionIds", scoped);
        assertThat(institutionScoped.getSql()).contains("course.institution_id IN");
        assertHandlersResolved(institutionScoped);
        BoundSql institutionCount = boundSql(configuration, "countByUserIdAndInstitutionIds", scoped);
        assertHandlersResolved(institutionCount);
    }

    private void assertHandlersResolved(BoundSql boundSql) {
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            assertThat(mapping.getTypeHandler())
                .as("type handler for %s", mapping.getProperty())
                .isNotNull();
        }
    }

    private BoundSql boundSql(
        Configuration configuration, String statement, Map<String, Object> parameters
    ) {
        MappedStatement mappedStatement =
            configuration.getMappedStatement(NAMESPACE + "." + statement);
        return mappedStatement.getBoundSql(parameters);
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (String resource : List.of(
            "mybatis/mapper/credential/CredentialMapper.xml", RESOURCE)) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
