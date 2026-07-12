package com.hotelpms.billing.client.dto;

import java.util.UUID;

/**
 * External Data Transfer Object representing a Guest response.
 *
 * @param id          the guest UUID
 * @param firstName   the guest's first name
 * @param lastName    the guest's last name
 * @param email       the guest's email address
 * @param fiscalCode  Codice Fiscale (optional)
 * @param vatNumber   Partita IVA (optional)
 * @param companyName company / legal entity name (optional)
 * @param sdiCode     SDI recipient code (optional)
 * @param pecEmail    PEC email (optional)
 */
public record GuestResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String fiscalCode,
        String vatNumber,
        String companyName,
        String sdiCode,
        String pecEmail) {
}
