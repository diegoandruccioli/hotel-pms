package com.hotelpms.reservation.mapper;

import com.hotelpms.reservation.domain.Reservation;
import com.hotelpms.reservation.domain.ReservationLineItem;
import com.hotelpms.reservation.dto.ReservationLineItemRequest;
import com.hotelpms.reservation.dto.ReservationLineItemResponse;
import com.hotelpms.reservation.dto.ReservationRequest;
import com.hotelpms.reservation.dto.ReservationResponse;
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
     * @param request the request
     * @return the entity
     */
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
     * @param request the request
     * @param entity  the entity
     */
    void updateEntityFromRequest(ReservationRequest request, @MappingTarget Reservation entity);
}
