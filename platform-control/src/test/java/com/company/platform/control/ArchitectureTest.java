package com.company.platform.control;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.company.platform.control", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersMustNotAccessMappers =
            noClasses().that().resideInAPackage("..control.controller..")
                    .should().dependOnClassesThat().resideInAPackage("..control.mapper..")
                    .because("控制器只通过 service 访问数据");

    @ArchTest
    static final ArchRule mappersMustNotDependOnUpperLayers =
            noClasses().that().resideInAPackage("..control.mapper..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..control.service..", "..control.controller..")
                    .because("mapper 是持久化原语，不应反向依赖业务层");

    @ArchTest
    static final ArchRule entitiesMustNotDependOnUpperLayers =
            noClasses().that().resideInAPackage("..control.entity..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..control.service..", "..control.controller..",
                            "..control.integration..")
                    .because("实体只描述表结构");

    @ArchTest
    static final ArchRule layeredArchitectureIsRespected =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Controller").definedBy("..control.controller..")
                    .layer("Service").definedBy("..control.service..")
                    .layer("Mapper").definedBy("..control.mapper..")
                    .layer("Integration").definedBy("..control.integration..")
                    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                    .whereLayer("Mapper").mayOnlyBeAccessedByLayers("Service")
                    .whereLayer("Integration").mayOnlyBeAccessedByLayers("Service");
}
