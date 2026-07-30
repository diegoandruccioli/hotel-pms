package com.hotelpms.internalauth.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ADR-004 rule builder, shared by every service's own
 * {@code TenantIsolationArchTest}: every custom query method on a repository
 * for a tenant-root entity (one carrying its own {@code hotel_id} column)
 * must scope by hotelId — either via the method name (Spring Data
 * derived-query convention already used everywhere else in this codebase) or
 * an explicit {@link TenantScopeExempt} documenting why it's safe without
 * one.
 *
 * <p>Found T-BILL-04 (billing-service) and T-STAY-06 (frontdesk-service)
 * while designing this rule — both were repository methods with no hotelId
 * parameter at all, one leaking every hotel's invoices to any Owner, the
 * other leaking a guest's stay history across hotels on the check-in
 * pre-fill. This rule exists to catch that class of bug before it ships, not
 * after.
 *
 * <p>Inherited {@code JpaRepository}/{@code CrudRepository} methods
 * (findById, save, delete, ...) are out of scope for this rule — ArchUnit's
 * {@code getMethods()} only returns methods declared directly on the
 * repository interface itself. Those are guarded at the call site instead
 * (the service layer uses {@code findByIdAndHotelId}, never the inherited
 * {@code findById}, for tenant-root entities).
 *
 * <p>Extracted from 5 near-identical per-service copies
 * (AUDIT_ANALISI_2026-07.md item 2) — only the actual scan target
 * ({@code @AnalyzeClasses(packages = ...)}, which must stay a compile-time
 * annotation on each service's own test class) and the per-service
 * {@code TENANT_ROOT_REPOSITORIES} set remain local.
 */
public final class TenantIsolationRules {

    private TenantIsolationRules() {
    }

    /**
     * Builds the ArchUnit rule for a service's set of tenant-root
     * repositories.
     *
     * @param tenantRootRepositories fully-qualified names of repository
     *                                interfaces whose entity carries its own
     *                                {@code hotel_id} column
     * @return the ArchRule to assign to an {@code @ArchTest} field
     */
    public static ArchRule customQueryMethodsMustScopeByHotelId(final Set<String> tenantRootRepositories) {
        return classes()
                .that(new DescribedPredicate<>("are tenant-root repositories") {
                    @Override
                    public boolean test(final JavaClass javaClass) {
                        return tenantRootRepositories.contains(javaClass.getFullName());
                    }
                })
                .should(new ArchCondition<>("declare every custom query method scoped by hotelId") {
                    @Override
                    public void check(final JavaClass javaClass, final ConditionEvents events) {
                        for (final JavaMethod method : javaClass.getMethods()) {
                            if (isScoped(method)) {
                                continue;
                            }
                            events.add(SimpleConditionEvent.violated(method, String.format(
                                    "%s.%s() has no hotelId in its name and no @TenantScopeExempt — "
                                            + "add hotelId to the query or document why it's safe without one",
                                    javaClass.getSimpleName(), method.getName())));
                        }
                    }

                    private boolean isScoped(final JavaMethod method) {
                        return method.getName().contains("HotelId")
                                || method.isAnnotatedWith(TenantScopeExempt.class);
                    }
                });
    }
}
