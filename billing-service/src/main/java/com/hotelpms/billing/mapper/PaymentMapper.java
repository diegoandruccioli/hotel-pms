package com.hotelpms.billing.mapper;

import com.hotelpms.billing.domain.Payment;
import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for Payment entities.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    /**
     * Converts a Payment entity to a PaymentResponse DTO.
     * 
     * @param payment the entity
     * @return the DTO
     */
    @Mapping(source = "invoice.id", target = "invoiceId")
    PaymentResponse toResponse(Payment payment);

    /**
     * Converts a PaymentRequest DTO to a Payment entity.
     * 
     * @param paymentRequest the DTO
     * @return the entity
     */
    @Mapping(target = "invoice", ignore = true)
    Payment toEntity(PaymentRequest paymentRequest);
}
