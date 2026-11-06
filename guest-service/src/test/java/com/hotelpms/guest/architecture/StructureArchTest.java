package com.hotelpms.guest.architecture;

import com.hotelpms.internalauth.architecture.StructureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.hotelpms.guest", importOptions = ImportOption.DoNotIncludeTests.class)
final class StructureArchTest {

    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_USE_REPOSITORIES = StructureRules.controllersMustNotUseRepositories();

    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_USE_ENTITIES = StructureRules.controllersMustNotUseEntities();

    @ArchTest
    static final ArchRule LOWER_LAYERS_MUST_NOT_USE_CONTROLLERS = StructureRules.servicesAndRepositoriesMustNotUseControllers();

    @ArchTest
    static final ArchRule REPOSITORIES_MUST_NOT_USE_SERVICES = StructureRules.repositoriesMustNotUseServices();

    private StructureArchTest() {
    }
}
