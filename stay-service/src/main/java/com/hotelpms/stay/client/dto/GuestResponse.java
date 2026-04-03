package com.hotelpms.stay.client.dto;

import java.util.UUID;

/**
 * Data Transfer Object representing a Guest from the Guest Service.
 *
 * @param id        the guest ID
 * @param firstName the guest's first name
 * @param lastName  the guest's last name
 * @param email     the guest's email
 */
public record GuestResponse(
        UUID id,
        String firstName,
        String lastName,
        String email) {
}
