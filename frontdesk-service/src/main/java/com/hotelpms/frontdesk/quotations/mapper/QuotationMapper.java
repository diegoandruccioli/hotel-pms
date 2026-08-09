package com.hotelpms.frontdesk.quotations.mapper;

import com.hotelpms.frontdesk.quotations.domain.QuotationLineItem;
import com.hotelpms.frontdesk.quotations.dto.QuotationLineItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * Mapper for QuotationLineItem. The top-level {@code Quotation}↔{@code
 * QuotationResponse} mapping is done by hand in {@code QuotationServiceImpl}
 * instead of MapStruct: {@code status} (computed {@code EXPIRED}) and {@code
 * guestFullName} (resolved via a cross-service call) aren't 1:1 field copies,
 * so a generated mapper would need as much manual glue as no mapper at all.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface QuotationMapper {

    /**
     * Converts a line item entity to its response DTO.
     *
     * @param entity the entity
     * @return the response
     */
    QuotationLineItemResponse toLineItemResponse(QuotationLineItem entity);

    /**
     * Converts a list of line item entities to response DTOs.
     *
     * @param entities the entities
     * @return the responses
     */
    List<QuotationLineItemResponse> toLineItemResponses(List<QuotationLineItem> entities);
}
