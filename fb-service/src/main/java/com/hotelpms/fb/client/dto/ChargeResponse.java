package com.hotelpms.fb.client.dto;

import java.util.UUID;

/**
 * Minimal response DTO received from billing-service after a charge is added.
 *
 * @param id the charge UUID assigned by billing-service
 */
public record ChargeResponse(UUID id) {
}
