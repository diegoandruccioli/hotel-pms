package com.hotelpms.internalauth.architecture;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Layering rules shared by every service's own {@code StructureArchTest}.
 *
 * <p>The services follow {@code Controller -> Service -> Repository -> Entity}
 * (see the backend rules). These builders turn that into checks that look at
 * package names only ({@code ..controller..}, {@code ..service..},
 * {@code ..repository..}), so they work both for layer-first services
 * ({@code com.hotelpms.billing.controller}) and feature-first ones
 * ({@code com.hotelpms.frontdesk.rooms.controller}).
 *
 * <p>As with {@link TenantIsolationRules}, the scan target
 * ({@code @AnalyzeClasses(packages = ...)}) stays a compile-time annotation on
 * each service's own test class. Each test wraps these rules in
 * {@code FreezingArchRule} so only new violations fail the build.
 */
public final class StructureRules {

    private static final String CONTROLLER = "..controller..";
    private static final String SERVICE = "..service..";
    private static final String REPOSITORY = "..repository..";
    private static final String JPA_ENTITY = "jakarta.persistence.Entity";

    private StructureRules() {
    }

    /**
     * Controllers go through a service, never straight to a repository.
     *
     * @return the ArchRule to assign to an {@code @ArchTest} field
     */
    public static ArchRule controllersMustNotUseRepositories() {
        return noClasses().that().resideInAPackage(CONTROLLER)
                .should().dependOnClassesThat().resideInAPackage(REPOSITORY)
                .because("controllers must go through the service layer");
    }

    /**
     * Controllers return DTOs, never JPA entities.
     *
     * @return the ArchRule to assign to an {@code @ArchTest} field
     */
    public static ArchRule controllersMustNotUseEntities() {
        return noClasses().that().resideInAPackage(CONTROLLER)
                .should().dependOnClassesThat().areAnnotatedWith(JPA_ENTITY)
                .because("JPA entities must not be exposed over REST: map them to DTOs");
    }

    /**
     * Lower layers do not reach back up into the web layer.
     *
     * @return the ArchRule to assign to an {@code @ArchTest} field
     */
    public static ArchRule servicesAndRepositoriesMustNotUseControllers() {
        return noClasses().that().resideInAnyPackage(SERVICE, REPOSITORY)
                .should().dependOnClassesThat().resideInAPackage(CONTROLLER)
                .because("dependencies point downwards: controller -> service -> repository");
    }

    /**
     * Repositories do not call services.
     *
     * @return the ArchRule to assign to an {@code @ArchTest} field
     */
    public static ArchRule repositoriesMustNotUseServices() {
        return noClasses().that().resideInAPackage(REPOSITORY)
                .should().dependOnClassesThat().resideInAPackage(SERVICE)
                .because("dependencies point downwards: controller -> service -> repository");
    }

    /**
     * Feature slices of a feature-first service ({@code <base>.<feature>..})
     * must not depend on each other in a cycle.
     *
     * @param basePackage the service root, e.g. {@code com.hotelpms.frontdesk}
     * @return the ArchRule to assign to an {@code @ArchTest} field
     */
    public static ArchRule featureSlicesMustBeFreeOfCycles(final String basePackage) {
        return slices().matching(basePackage + ".(*)..")
                .should().beFreeOfCycles();
    }
}
