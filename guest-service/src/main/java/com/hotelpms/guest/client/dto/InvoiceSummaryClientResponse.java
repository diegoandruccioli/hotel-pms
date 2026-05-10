package com.hotelpms.guest.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Feign client DTO mirroring {@code InvoiceSummaryResponse} from billing-service.
 * Used exclusively by the GDPR Art. 20 data-export.
 *
 * @param invoiceId     the invoice UUID
 * @param invoiceNumber the human-readable invoice number
 * @param issueDate     the issue timestamp
 * @param totalAmount   the total amount charged
 * @param status        the invoice status as a string
 */
public record InvoiceSummaryClientResponse(
        UUID invoiceId,
        String invoiceNumber,
        LocalDateTime issueDate,
        BigDecimal totalAmount,
        String status) {
}
