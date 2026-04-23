package com.hotelpms.billing.mapper;

import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for InvoiceCharge entities.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InvoiceChargeMapper {

    /**
     * Converts an InvoiceCharge entity to a ChargeResponse DTO.
     *
     * @param charge the entity
     * @return the DTO
     */
    @Mapping(target = "invoiceId", source = "invoice.id")
    ChargeResponse toResponse(InvoiceCharge charge);

    /**
     * Converts a ChargeRequest DTO to an InvoiceCharge entity.
     * The parent invoice reference is excluded — set by the service layer.
     *
     * @param request the DTO
     * @return the entity
     */
    @Mapping(target = "invoice", ignore = true)
    InvoiceCharge toEntity(ChargeRequest request);
}
