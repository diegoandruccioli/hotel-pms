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
     * Runs every check {@link #generateXml} would run — eligibility, VAT reconciliation,
     * hotel fiscal identity, guest structured address, schema validation — without
     * generating a recorded export or locking the invoice. A side-effect-free preflight
     * so a caller (the frontend's hidden-iframe download, which can't observe an HTTP
     * error response) can surface a real error before committing to the recording
     * download.
     *
     * @param invoiceId the invoice UUID
     * @throws com.hotelpms.billing.exception.BillingValidationException if any fiscal
     *                                                                   identity/address/eligibility check fails
     * @throws com.hotelpms.billing.exception.InvoiceConflictException  if the invoice is CANCELLED or RICEVUTA
     */
    void validateXmlGeneration(@NonNull UUID invoiceId);

    /**
     * Builds a ZIP archive containing one FatturaPA XML per eligible (FATTURA,
     * non-CANCELLED) invoice issued within the given period for the caller's hotel,
     * plus a CSV index — the batch hand-off to the commercialista/third-party
     * accounting software for a full period at once.
     *
     * <p>When {@code dryRun} is {@code false}, each included invoice is
     * individually schema-validated and fiscally recorded (permanently locked)
     * exactly as a single-invoice export via {@link #generateXml}. When
     * {@code dryRun} is {@code true}, the same XML is generated and validated
     * for preview purposes only — nothing is recorded and no invoice is locked,
     * so the caller can inspect the would-be export before committing to it.
     *
     * @param from   inclusive lower bound (day) on invoice issue date
     * @param to     inclusive upper bound (day) on invoice issue date
     * @param dryRun {@code true} for a preview that locks nothing; {@code false}
     *               to actually record and lock every eligible invoice
     * @return ZIP archive bytes
     */
    byte[] generateBatchZip(@NonNull LocalDate from, @NonNull LocalDate to, boolean dryRun);
}
