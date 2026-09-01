package com.hotelpms.frontdesk.reservations.mapper;

import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper for Reservation and ReservationLineItem.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface ReservationMapper {

    /**
     * Maps Request DTO to Entity.
     *
     * <p>{@code version} is deliberately excluded: it is JPA's own {@code @Version}
     * optimistic-lock counter on the entity, not a value a client should ever seed
     * directly on a brand-new row. {@code ReservationRequest.version} exists only for
     * the client to echo back its last-read version on update (see {@code
     * ReservationServiceImpl#verifyNotStale}) — it must never reach {@code
     * Reservation.version} through this mapping.
     *
     * @param request the request
     * @return the entity
     */
    @Mapping(target = "version", ignore = true)
    Reservation toEntity(ReservationRequest request);

    /**
     * Maps Request DTO to Entity.
     *
     * @param request the request
     * @return the entity
     */
    ReservationLineItem toEntity(ReservationLineItemRequest request);

    /**
     * Maps Entity to Response DTO.
     *
     * @param entity the entity
     * @return the response
     */
    @Mapping(target = "guestFullName", constant = "ENRICHMENT_PENDING")
    ReservationResponse toResponse(Reservation entity);

    /**
     * Maps Entity to Response DTO.
     *
     * @param entity the entity
     * @return the response
     */
    ReservationLineItemResponse toResponse(ReservationLineItem entity);

    /**
     * Updates Entity from Request DTO.
     *
     * <p>{@code lineItems} is deliberately excluded: {@code
     * ReservationServiceImpl#updateReservation} rebuilds and prices line
     * items manually, before they're added to the managed {@code
     * existingReservation} collection. Letting this mapper's generated
     * clear+addAll touch {@code lineItems} too would add the new (still
     * unpriced) entities to the managed, cascade-persisted collection before
     * price resolution runs — Hibernate can snapshot the INSERT statement's
     * bound values at that earlier point, so a later {@code setPrice()} call
     * silently never reaches the database (NOT NULL violation on {@code
     * reservation_line_items.price}).
     *
     * <p>{@code version} is also excluded, same reasoning as {@link #toEntity
     * (ReservationRequest)}: it must stay whatever Hibernate's optimistic-lock
     * machinery has it as, never a value copied in from the request body. The staleness
     * check itself ({@code ReservationServiceImpl#verifyNotStale}) already ran, against
     * {@code entity}'s version, before this method is called.
     *
     * @param request the request
     * @param entity  the entity
     */
    @Mapping(target = "lineItems", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromRequest(ReservationRequest request, @MappingTarget Reservation entity);
}
