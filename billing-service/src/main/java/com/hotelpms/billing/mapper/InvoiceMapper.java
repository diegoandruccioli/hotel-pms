package com.hotelpms.billing.mapper;

import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for Invoice entities.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = PaymentMapper.class)
public interface InvoiceMapper {

    /**
     * Converts an Invoice entity to an InvoiceResponse DTO.
     * 
     * @param invoice the entity
     * @return the DTO
     */
    InvoiceResponse toResponse(Invoice invoice);

    /**
     * Converts an InvoiceRequest DTO to an Invoice entity.
     * 
     * @param invoiceRequest the DTO
     * @return the entity
     */
    @Mapping(target = "payments", ignore = true)
    Invoice toEntity(InvoiceRequest invoiceRequest);
}
