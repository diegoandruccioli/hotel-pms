package com.hotelpms.frontdesk.stays.repository;

import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for per-hotel operational settings.
 *
 * <p>{@code hotelId} is this entity's own {@code @Id} (one row per hotel), so
 * every lookup was already hotel-scoped by construction via the inherited
 * {@code findById} — but that left this repository declaring zero custom
 * methods, which made the tenant-isolation ArchUnit rule vacuous for it (it
 * had nothing to check). {@link #findByHotelId(UUID)} is a deliberate,
 * explicit synonym for {@code findById}: it gives the rule something real to
 * verify and makes call-site intent self-documenting (Point 7 item 2,
 * tenant-isolation audit). {@code findAll()} is still used once, deliberately
 * unscoped, by the scheduled night-audit job to enumerate every hotel before
 * looping per-tenant — see {@code NightAuditServiceImpl.runScheduledNightAudit}.
 */
public interface HotelSettingsRepository extends JpaRepository<HotelSettings, UUID> {

    /**
     * Explicit, hotelId-scoped synonym for {@code findById} (see class javadoc).
     *
     * @param hotelId the hotel's UUID, which is also this entity's {@code @Id}
     * @return the hotel's settings row, if it exists
     */
    Optional<HotelSettings> findByHotelId(UUID hotelId);
}
