package com.hotelpms.billing.service;

import org.springframework.lang.NonNull;

import java.util.UUID;

/**
 * Generates FatturaPA-compliant XML (format FPR12) for Italian electronic invoicing via SDI.
 * Only invoices with documentType=FATTURA are eligible.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
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
}
