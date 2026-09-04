package com.adn.dabaeum.blockchain.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BlockchainRegistryPortContractTest {

    @Test
    void port_exposes_only_provider_neutral_business_operations() {
        Set<String> methodNames = Arrays.stream(BlockchainRegistryPort.class.getDeclaredMethods())
            // 커버리지 계측기가 인터페이스에 넣는 합성 메서드($jacocoInit 등)는 계약이 아니다.
            .filter(method -> !method.isSynthetic())
            .filter(method -> !method.getName().startsWith("$"))
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(methodNames).containsExactlyInAnyOrder(
            "createCredentialState",
            "getCredentialState",
            "updateCredentialState",
            "reissueCredentialState",
            "getCredentialHistory",
            // 원장 블록 높이. 제공자 고유 타입을 노출하지 않는 모니터링 조회다.
            "ledgerHeight"
        );
        assertThat(methodNames).noneMatch(name -> name.toLowerCase().contains("delete"));
    }

    @Test
    void history_operation_uses_reference_and_bounded_query_contract() throws Exception {
        Method method = BlockchainRegistryPort.class.getDeclaredMethod(
            "getCredentialHistory",
            CredentialRegistryReference.class,
            BlockchainHistoryQuery.class
        );

        assertThat(method.getGenericReturnType().getTypeName())
            .isEqualTo(List.class.getName() + "<" + BlockchainHistoryEntry.class.getName() + ">");
    }

    @Test
    void blockchain_domain_does_not_depend_on_fabric_or_grpc_types() {
        var classes = new ClassFileImporter()
            .importPackages("com.adn.dabaeum.blockchain.domain");

        noClasses()
            .that().resideInAPackage("com.adn.dabaeum.blockchain.domain")
            .should().dependOnClassesThat().resideInAnyPackage("org.hyperledger..", "io.grpc..")
            .check(classes);
    }
}
