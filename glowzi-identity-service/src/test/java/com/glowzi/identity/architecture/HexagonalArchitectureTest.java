package com.glowzi.identity.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests that ENFORCE the hexagonal dependency rules at build time.
 *
 * If anyone accidentally imports a JPA class inside domain/, this test fails.
 * Think of it as a "compiler for your architecture."
 */
@AnalyzeClasses(
        packages = "com.glowzi.identity",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {

    // ── RULE 1: domain/ must NEVER depend on application/, infrastructure/, or interfaces/
    @ArchTest
    static final ArchRule domain_should_not_depend_on_application =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_interfaces =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    // ── RULE 2: application/ must NEVER depend on interfaces/ (this was the original violation)
    @ArchTest
    static final ArchRule application_should_not_depend_on_interfaces =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    // ── RULE 3: application/ must NEVER depend on infrastructure/
    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    // ── RULE 4: domain/ should not use Spring framework
    @ArchTest
    static final ArchRule domain_should_not_use_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    // ── RULE 5: domain/ should not use JPA
    @ArchTest
    static final ArchRule domain_should_not_use_jpa =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");
}
