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
 * @param address     full street address, or {@code null} if unset
 * @param cap         CAP — Italian 5-digit postal code, or {@code null} if unset
 * @param comune      Comune — municipality name, or {@code null} if unset
 * @param provincia   Provincia — 2-letter province code, or {@code null} if unset
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
        String pecEmail,
        String address,
        String cap,
        String comune,
        String provincia) {
}
