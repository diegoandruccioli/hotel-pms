package com.hotelpms.frontdesk.client.dto;

import java.util.UUID;

/**
 * Request DTO to open a MASTER folio in billing-service for a reservation group.
 *
 * @param guestId the group's contact/responsible guest, used as the invoice's guestId
 */
public record MasterFolioRequest(UUID guestId) {
}
