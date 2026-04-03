package com.hotelpms.reservation.client.dto;

import java.util.UUID;

/**
 * DTO for Guest response from Guest Service.
 *
 * @param id        the guest ID
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
