package com.adn.dabaeum.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.adn.dabaeum",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DomainPurityTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworks =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "org.mybatis..",
                "java.sql..",
                "jakarta.servlet..",
                "jakarta.persistence..",
                "org.hibernate.."
            )
            .because(
                "domain 계층은 Framework와 영속성 기술로부터 독립적이어야 한다"
            )
            .allowEmptyShould(true);
}
