package com.adn.dabaeum.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Service;

@AnalyzeClasses(
    packages = "com.adn.dabaeum",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class MyBatisBoundaryTest {

    @ArchTest
    static final ArchRule myBatisMapperTypesMustBeAnnotated =
        classes()
            .that().resideInAPackage("..infrastructure.mybatis..")
            .and().haveSimpleNameEndingWith("Mapper")
            .should().beAnnotatedWith(Mapper.class)
            .because(
                "infrastructure.mybatis의 Mapper 타입은 @Mapper로 선언해야 한다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule annotatedMappersMustResideInMyBatisInfrastructure =
        classes()
            .that().areAnnotatedWith(Mapper.class)
            .should().resideInAPackage("..infrastructure.mybatis..")
            .because(
                "@Mapper 타입은 infrastructure.mybatis 경계 내부에 있어야 한다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersMustNotDependDirectlyOnMappers =
        noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat()
            .areAnnotatedWith(Mapper.class)
            .because(
                "Controller는 MyBatis @Mapper에 직접 의존하면 안 된다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule serviceNamedTypesMustNotDependDirectlyOnMappers =
        noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat()
            .areAnnotatedWith(Mapper.class)
            .because(
                "Service는 Repository Port를 사용하고 MyBatis @Mapper에 직접 의존하면 안 된다"
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule annotatedServicesMustNotDependDirectlyOnMappers =
        noClasses()
            .that().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat()
            .areAnnotatedWith(Mapper.class)
            .because(
                "@Service 타입은 MyBatis @Mapper에 직접 의존하면 안 된다"
            )
            .allowEmptyShould(true);
}
