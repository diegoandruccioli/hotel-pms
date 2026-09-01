package com.hotelpms.frontdesk.stays.mapper;

import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for translating Stay entities and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StayMapper {

    /**
     * Converts a Stay entity into a StayResponse DTO. {@code cityTaxWarning} is left
     * null — it doesn't live on the entity, only ever non-null right after check-in
     * (see the overload below).
     *
     * @param stay the stay entity
     * @return the StayResponse DTO
     */
    StayResponse toDto(Stay stay);

    /**
     * Converts a Stay entity into a StayResponse DTO, attaching a {@code
     * cityTaxWarning} that doesn't live on the entity itself (Parte 5.3: the
     * check-in response surfaces a tourist-tax configuration gap immediately,
     * not only later via the Dashboard summary). Goes through the same generated
     * mapping as {@link #toDto(Stay)} rather than a hand-built copy, so a field
     * added to {@code StayResponse} is picked up here automatically instead of
     * needing a second, easy-to-miss update.
     *
     * @param stay           the stay entity
     * @param cityTaxWarning the check-in-time warning to attach, or {@code null}
     * @return the StayResponse DTO
     */
    @Mapping(target = "cityTaxWarning", source = "cityTaxWarning")
    StayResponse toDto(Stay stay, CityTaxUnassessedReason cityTaxWarning);

    /**
     * Converts a StayRequest DTO to a Stay entity.
     *
     * @param request the stay request
     * @return the stay entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "invoiceId", ignore = true)
    @Mapping(target = "alloggiatiSent", ignore = true)
    @Mapping(target = "alloggiatiSendFailed", ignore = true)
    @Mapping(target = "alloggiatiFailureReason", ignore = true)
    @Mapping(target = "invoiceCreationFailed", ignore = true)
    @Mapping(target = "invoiceCreationFailureReason", ignore = true)
    @Mapping(target = "checkoutEmailFailed", ignore = true)
    @Mapping(target = "checkoutEmailFailureReason", ignore = true)
    @Mapping(target = "guestDisplayName", ignore = true)
    @Mapping(target = "roomNumber", ignore = true)
    Stay toEntity(StayRequest request);

    /**
     * Updates an existing Stay entity with data from a StayRequest DTO.
     *
     * @param request the stay request containing new data
     * @param stay    the entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "invoiceId", ignore = true)
    @Mapping(target = "alloggiatiSent", ignore = true)
    @Mapping(target = "alloggiatiSendFailed", ignore = true)
    @Mapping(target = "alloggiatiFailureReason", ignore = true)
    @Mapping(target = "invoiceCreationFailed", ignore = true)
    @Mapping(target = "invoiceCreationFailureReason", ignore = true)
    @Mapping(target = "checkoutEmailFailed", ignore = true)
    @Mapping(target = "checkoutEmailFailureReason", ignore = true)
    @Mapping(target = "guestDisplayName", ignore = true)
    @Mapping(target = "roomNumber", ignore = true)
    void updateEntityFromDto(StayRequest request, @MappingTarget Stay stay);
}
