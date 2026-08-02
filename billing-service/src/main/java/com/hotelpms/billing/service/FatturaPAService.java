package com.hotelpms.billing.service;

import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generates FatturaPA-compliant XML (format FPR12) for Italian electronic invoicing via SDI.
 * Only invoices with documentType=FATTURA are eligible.
 */
public interface FatturaPAService {

    /**
     * Builds a FatturaPA FPR12 XML document for the given invoice.
     * Throws a 409 conflict if the invoice is CANCELLED or has documentType=RICEVUTA.
     * Throws 404 if the invoice does not exist in the caller's hotel scope.
     *
     * @param invoiceId the invoice UUID
     * @return UTF-8 encoded XML bytes, schema-conformant to FPR12
     */
    byte[] generateXml(@NonNull UUID invoiceId);

    /**
     * Builds a ZIP archive containing one FatturaPA XML per eligible (FATTURA,
     * non-CANCELLED) invoice issued within the given period for the caller's hotel,
     * plus a CSV index — the batch hand-off to the commercialista/third-party
     * accounting software for a full period at once. Each included invoice is
     * individually schema-validated and fiscally recorded exactly as a
     * single-invoice export via {@link #generateXml}.
     *
     * @param from inclusive lower bound (day) on invoice issue date
     * @param to   inclusive upper bound (day) on invoice issue date
     * @return ZIP archive bytes
     */
    byte[] generateBatchZip(@NonNull LocalDate from, @NonNull LocalDate to);
}
