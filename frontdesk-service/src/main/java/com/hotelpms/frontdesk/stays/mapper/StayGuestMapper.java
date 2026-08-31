package com.hotelpms.frontdesk.stays.mapper;

import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.dto.StayGuestRequest;
import com.hotelpms.frontdesk.stays.dto.StayGuestResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for translating {@link StayGuest} entities and DTOs for the guest-lifecycle
 * endpoints (Parte 1) — distinct from the implicit guest sub-mapping {@link StayMapper}
 * generates for the check-in payload, since these operate on one existing guest at a
 * time and must never touch the lifecycle fields ({@code arrivalDate},
 * {@code alloggiatiSent}, {@code needsResubmit}, ...) the service layer owns.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StayGuestMapper {

    /**
     * Converts a StayGuest entity into a StayGuestResponse DTO.
     *
     * @param guest the stay guest entity
     * @return the StayGuestResponse DTO
     */
    StayGuestResponse toResponse(StayGuest guest);

    /**
     * Converts a StayGuestRequest DTO to a new StayGuest entity. Lifecycle fields are
     * left unset for the caller to stamp explicitly.
     *
     * <p>{@code isPrimaryGuest} is also excluded: a guest added mid-stay via {@code
     * addGuest} must never become primary just because the request happened to carry
     * that flag — the stay already has a primary guest from check-in, and promoting one
     * is a deliberate, single-purpose operation ({@link
     * com.hotelpms.frontdesk.stays.service.StayGuestService#promotePrimary}), never a
     * side effect of adding someone else.
     *
     * @param request the stay guest request
     * @return the stay guest entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "stay", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "arrivalDate", ignore = true)
    @Mapping(target = "departureDate", ignore = true)
    @Mapping(target = "alloggiatiSent", ignore = true)
    @Mapping(target = "alloggiatiSentAt", ignore = true)
    @Mapping(target = "needsResubmit", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "isPrimaryGuest", ignore = true)
    @Mapping(target = "cityTaxChargeId", ignore = true)
    @Mapping(target = "cityTaxChargeAmount", ignore = true)
    StayGuest toEntity(StayGuestRequest request);

    /**
     * Updates an existing StayGuest entity's anagraphic/document fields from a request —
     * never the lifecycle fields, which the service layer manages (e.g. flipping
     * {@code needsResubmit} when a correction follows a successful send).
     *
     * <p>{@code isPrimaryGuest} is excluded for the same reason as {@link
     * #toEntity(StayGuestRequest)}: editing a guest's anagraphic data must never change
     * who the stay's primary guest is as a side effect — the invariant of exactly one
     * primary guest per stay is owned entirely by {@code promotePrimary}, which
     * atomically demotes every other guest in the same write.
     *
     * @param request the stay guest request containing new data
     * @param guest   the entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "stay", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "arrivalDate", ignore = true)
    @Mapping(target = "departureDate", ignore = true)
    @Mapping(target = "alloggiatiSent", ignore = true)
    @Mapping(target = "alloggiatiSentAt", ignore = true)
    @Mapping(target = "needsResubmit", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "primaryGuest", ignore = true)
    @Mapping(target = "cityTaxChargeId", ignore = true)
    @Mapping(target = "cityTaxChargeAmount", ignore = true)
    void updateEntityFromRequest(StayGuestRequest request, @MappingTarget StayGuest guest);
}
