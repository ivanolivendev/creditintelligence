package com.creditintelligence;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class ArchitectureTest {
    private static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "..infrastructure..");

    private static final ArchRule DOMAIN_IS_NOT_A_SPRING_COMPONENT = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Component.class);

    private static final ArchRule CONTROLLERS_DO_NOT_DEPEND_ON_PERSISTENCE = noClasses()
            .that().resideInAnyPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

    private static final ArchRule INFRASTRUCTURE_IS_NOT_IMPORTED_BY_DOMAIN = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..");

    private static final ArchRule APPLICATION_SERVICES_ARE_NAMED_CONSISTENTLY = classes()
            .that().resideInAnyPackage("..application..")
            .and().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("Service");

    private static final ArchRule PROPOSAL_AND_POLICY_APPLICATIONS_HIDE_PERSISTENCE = noClasses()
            .that().resideInAnyPackage("..proposal.application..", "..policy.application..",
                    "..document.application..", "..decision.application..", "..analysis.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

    @Test
    void domain_rules_are_respected() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.creditintelligence");
        DOMAIN_IS_FRAMEWORK_FREE.check(classes);
        DOMAIN_IS_NOT_A_SPRING_COMPONENT.check(classes);
        CONTROLLERS_DO_NOT_DEPEND_ON_PERSISTENCE.check(classes);
        INFRASTRUCTURE_IS_NOT_IMPORTED_BY_DOMAIN.check(classes);
        APPLICATION_SERVICES_ARE_NAMED_CONSISTENTLY.check(classes);
        PROPOSAL_AND_POLICY_APPLICATIONS_HIDE_PERSISTENCE.check(classes);
    }
}
