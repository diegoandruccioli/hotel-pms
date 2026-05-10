package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Minimal invoice summary for GDPR Art. 20 data-export.
 * Contains only the fields relevant to the data subject's right of portability.
 *
 * @param invoiceId     the invoice UUID
 * @param invoiceNumber the human-readable invoice number (null if not yet issued)
 * @param issueDate     the issue timestamp
 * @param totalAmount   the total amount charged
 * @param status        the invoice status
 */
public record InvoiceSummaryResponse(
        UUID invoiceId,
        String invoiceNumber,
        LocalDateTime issueDate,
        BigDecimal totalAmount,
        InvoiceStatus status) {
}
