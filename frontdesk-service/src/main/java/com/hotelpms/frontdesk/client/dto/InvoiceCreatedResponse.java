package com.hotelpms.frontdesk.client.dto;

import java.util.UUID;

/**
 * Minimal response DTO for a newly created invoice.
 * Only the invoice UUID is needed by the stays domain to persist the folio reference.
 *
 * @param id the invoice UUID
 */
public record InvoiceCreatedResponse(UUID id) {
}
