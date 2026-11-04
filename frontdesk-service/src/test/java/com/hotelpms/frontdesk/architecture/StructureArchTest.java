package com.hotelpms.frontdesk.architecture;

import com.hotelpms.internalauth.architecture.StructureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(packages = "com.hotelpms.frontdesk", importOptions = ImportOption.DoNotIncludeTests.class)
final class StructureArchTest {

    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_USE_REPOSITORIES = StructureRules.controllersMustNotUseRepositories();

    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_USE_ENTITIES = StructureRules.controllersMustNotUseEntities();

    @ArchTest
    static final ArchRule LOWER_LAYERS_MUST_NOT_USE_CONTROLLERS = StructureRules.servicesAndRepositoriesMustNotUseControllers();

    @ArchTest
    static final ArchRule REPOSITORIES_MUST_NOT_USE_SERVICES = StructureRules.repositoriesMustNotUseServices();

    /**
     * Frozen: the feature slices already depend on each other in cycles
     * (citytax/stays, pricing/rooms/reservations, ...). Breaking those is a
     * tenant/fiscal-sensitive refactor of its own; the store in
     * {@code src/test/resources/archunit_store} makes only NEW cycles fail.
     */
    @ArchTest
    static final ArchRule FEATURE_SLICES_MUST_BE_FREE_OF_CYCLES =
            FreezingArchRule.freeze(StructureRules.featureSlicesMustBeFreeOfCycles("com.hotelpms.frontdesk"));

    private StructureArchTest() {
    }
}
