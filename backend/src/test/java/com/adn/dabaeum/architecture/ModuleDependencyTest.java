package com.adn.dabaeum.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.adn.dabaeum",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleDependencyTest {

    @ArchTest
    static final ArchRule apiMustNotDependOnInfrastructure =
        noClasses()
            .that().resideInAPackage("com.adn.dabaeum..api..")
            .and().resideOutsideOfPackage("com.adn.dabaeum.common.api..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because(
                "Feature API는 response 변환을 위해 domain을 읽을 수 있지만 infrastructure에는 직접 의존하지 않아야 한다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationMustNotDependOnFeatureApi =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..institution.api..",
                "..system.api.."
            )
            .because(
                "application 계층은 feature API에 의존하지 않아야 한다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because(
                "application 계층은 infrastructure에 직접 의존하지 않아야 한다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainMustNotDependOnOuterLayers =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..api..",
                "..application..",
                "..infrastructure.."
            )
            .because(
                "domain 계층은 외부 계층에 의존하지 않아야 한다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule infrastructureMustNotDependOnApi =
        noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat()
            .resideInAPackage("..api..")
            .because(
                "infrastructure 계층은 API에 의존하지 않아야 한다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule infrastructureMustNotDependOnApplicationOutsidePorts =
        noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat(
                resideInAPackage("..application..")
                    .and(resideOutsideOfPackage("..application.port.."))
            )
            .because(
                "infrastructure 계층은 application port 외의 application 코드에 의존하지 않아야 한다"
            )
            .allowEmptyShould(true);
}
