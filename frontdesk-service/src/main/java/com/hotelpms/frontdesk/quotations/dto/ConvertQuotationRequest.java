package com.hotelpms.frontdesk.quotations.dto;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/quotations/{id}/convert}. Both fields
 * are optional at the transport level: an empty/absent body is valid when the
 * quotation has exactly one option — {@link com.hotelpms.frontdesk.quotations.service.impl.QuotationServiceImpl}
 * rejects a missing {@code optionId} only when the quotation actually has more
 * than one.
 *
 * @param optionId the option the guest chose, or {@code null}
 */
public record ConvertQuotationRequest(UUID optionId) {
}
