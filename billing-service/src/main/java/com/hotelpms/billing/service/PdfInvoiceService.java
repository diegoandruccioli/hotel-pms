package com.hotelpms.billing.service;

import java.util.UUID;



/**
 * Generates PDF representations of invoices for download.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface PdfInvoiceService {

    /**
     * Builds a PDF for the given invoice, including hotel header, charge lines, and payment summary.
     * The hotel is resolved from the current security context (same tenant as the caller).
     *
     * @param invoiceId the invoice UUID
     * @return raw PDF bytes ready to stream as {@code application/pdf}
     */
    byte[] generateInvoicePdf(UUID invoiceId);
}
