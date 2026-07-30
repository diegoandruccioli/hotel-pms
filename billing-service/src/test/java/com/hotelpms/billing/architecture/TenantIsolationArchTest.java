package com.hotelpms.billing.architecture;

import com.hotelpms.internalauth.architecture.TenantIsolationRules;
import com.hotelpms.internalauth.architecture.TenantScopeExempt;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Set;

/**
 * ADR-004: every custom query method on a repository for a tenant-root entity
 * (one carrying its own {@code hotel_id} column) must scope by hotelId —
 * either via the method name (Spring Data derived-query convention already
 * used everywhere else in this codebase) or an explicit
 * {@link TenantScopeExempt} documenting why it's safe without one.
 *
 * <p>Found T-BILL-04 (cross-tenant financial data leak) while designing this
 * rule — a repository method with no hotelId parameter at all, feeding a
 * report endpoint that returned every hotel's invoices to any Owner. This
 * rule exists to catch that class of bug before it ships, not after.
 *
 * <p>Inherited {@code JpaRepository}/{@code CrudRepository} methods
 * (findById, save, delete, ...) are out of scope for this rule — ArchUnit's
 * {@code getMethods()} only returns methods declared directly on the
 * repository interface itself. Those are guarded at the call site instead
 * (the service layer uses {@code findByIdAndHotelId}, never the inherited
 * {@code findById}, for tenant-root entities).
 */
@AnalyzeClasses(packages = "com.hotelpms.billing", importOptions = ImportOption.DoNotIncludeTests.class)
final class TenantIsolationArchTest {

    /**
     * Fully-qualified names of repository interfaces whose entity carries its
     * own {@code hotel_id} column. Update this list when a new tenant-root
     * entity/repository is introduced — the rule only inspects interfaces
     * named here.
     */
    private static final Set<String> TENANT_ROOT_REPOSITORIES = Set.of(
            "com.hotelpms.billing.repository.InvoiceRepository",
            "com.hotelpms.billing.repository.InvoiceSequenceRepository");

    @ArchTest
    static final ArchRule CUSTOM_QUERY_METHODS_ON_TENANT_ROOT_REPOSITORIES_MUST_SCOPE_BY_HOTEL_ID =
            TenantIsolationRules.customQueryMethodsMustScopeByHotelId(TENANT_ROOT_REPOSITORIES);

    private TenantIsolationArchTest() {
    }
}
