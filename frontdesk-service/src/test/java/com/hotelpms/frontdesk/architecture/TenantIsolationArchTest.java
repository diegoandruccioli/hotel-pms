package com.hotelpms.frontdesk.architecture;

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
 * <p>Found T-BILL-04 (billing-service) and T-STAY-06 (this service) while
 * designing this rule — both were repository methods with no hotelId
 * parameter at all, one leaking every hotel's invoices to any Owner, the
 * other leaking a guest's stay history across hotels on the check-in
 * pre-fill. This rule exists to catch that class of bug before it ships,
 * not after.
 *
 * <p>Inherited {@code JpaRepository}/{@code CrudRepository} methods
 * (findById, save, delete, ...) are out of scope for this rule — ArchUnit's
 * {@code getMethods()} only returns methods declared directly on the
 * repository interface itself. Those are guarded at the call site instead
 * (the service layer uses {@code findByIdAndHotelId}, never the inherited
 * {@code findById}, for tenant-root entities).
 */
@AnalyzeClasses(packages = "com.hotelpms.frontdesk", importOptions = ImportOption.DoNotIncludeTests.class)
final class TenantIsolationArchTest {

    /**
     * Fully-qualified names of repository interfaces whose entity carries its
     * own {@code hotel_id} column. Update this list when a new tenant-root
     * entity/repository is introduced — the rule only inspects interfaces
     * named here.
     *
     * <p>{@code RoomTypeRepository} was added here for T-ROOM-02 (2026-07-18):
     * {@code RoomType} had no {@code hotel_id} column since the V1 baseline
     * migration — any hotel's ADMIN/OWNER could read, tamper with (price, name,
     * capacity) or soft-delete another hotel's room-type catalog. Fixed with
     * V7 (hotel_id + per-hotel unique constraint). The
     * {@code RoomTypeServiceImpl} {@code @Cacheable("roomTypes")} cache keys
     * were updated in the same fix to include hotelId — a reminder for future
     * schema gaps of this shape: fixing the repository query alone is not
     * enough if a cache sits in front of it.
     */
    private static final Set<String> TENANT_ROOT_REPOSITORIES = Set.of(
            "com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository",
            "com.hotelpms.frontdesk.reservations.repository.ReservationRepository",
            "com.hotelpms.frontdesk.rooms.repository.RoomRepository",
            "com.hotelpms.frontdesk.stays.repository.StayRepository",
            "com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository");

    @ArchTest
    static final ArchRule CUSTOM_QUERY_METHODS_ON_TENANT_ROOT_REPOSITORIES_MUST_SCOPE_BY_HOTEL_ID =
            TenantIsolationRules.customQueryMethodsMustScopeByHotelId(TENANT_ROOT_REPOSITORIES);

    private TenantIsolationArchTest() {
    }
}
