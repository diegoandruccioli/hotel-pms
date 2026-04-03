package com.hotelpms.billing.client.dto;

import java.util.UUID;

/**
 * External Data Transfer Object representing a Guest response.
 *
 * @param id        the guest UUID
 * @param firstName the guest's first name
 * @param lastName  the guest's last name
 * @param email     the guest's email address
 */
public record GuestResponse(
        UUID id,
        String firstName,
        String lastName,
        String email) {
}
