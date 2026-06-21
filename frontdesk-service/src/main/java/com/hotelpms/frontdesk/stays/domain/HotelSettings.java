package com.hotelpms.frontdesk.stays.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-hotel operational settings. One row per hotel; hotel_id is the natural primary key.
 */
@Entity
@Table(name = "hotel_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class HotelSettings {

    private static final int LEN_HOTEL_NAME = 150;
    private static final int LEN_ADDRESS = 200;
    private static final int LEN_FISCAL = 20;
    private static final int LEN_LOGO_URL = 500;
    private static final int LEN_ALLOGGIATI_USERNAME = 100;

    /** The hotel this settings row belongs to (primary key). */
    @Id
    @Column(name = "hotel_id")
    private UUID hotelId;

    /**
     * When {@code true}, the Alloggiati Web report is automatically submitted to the
     * Polizia di Stato portal at each check-in.
     */
    @Column(name = "alloggiati_auto_send", nullable = false)
    private boolean alloggiatiAutoSend;

    /** Display name of the hotel property (e.g. "Hotel Bella Vista"). */
    @Column(name = "hotel_name", length = LEN_HOTEL_NAME)
    private String hotelName;

    /** Street address including civic number (e.g. "Via Roma 12"). */
    @Column(name = "address", length = LEN_ADDRESS)
    private String address;

    /** Partita IVA — Italian VAT number (11 digits). */
    @Column(name = "vat_number", length = LEN_FISCAL)
    private String vatNumber;

    /** Codice Fiscale — Italian fiscal code. */
    @Column(name = "fiscal_code", length = LEN_FISCAL)
    private String fiscalCode;

    /** Optional URL of the hotel logo image. */
    @Column(name = "logo_url", length = LEN_LOGO_URL)
    private String logoUrl;

    /**
     * Per-hotel Alloggiati Web portal username. Not a secret on its own (a portal
     * login name), so it is stored and returned in plain text.
     */
    @Column(name = "alloggiati_username", length = LEN_ALLOGGIATI_USERNAME)
    private String alloggiatiUsername;

    /**
     * Per-hotel Alloggiati Web portal password, AES-GCM encrypted via
     * {@link com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor}.
     * Never exposed in any response DTO.
     */
    @Column(name = "alloggiati_password_encrypted")
    private String alloggiatiPasswordEncrypted;

    /**
     * Per-hotel Alloggiati Web WsKey, AES-GCM encrypted via
     * {@link com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor}.
     * Never exposed in any response DTO.
     */
    @Column(name = "alloggiati_ws_key_encrypted")
    private String alloggiatiWsKeyEncrypted;

    /** The timestamp when the record was created. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** The timestamp when the record was last updated. */
    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    /**
     * Whether this hotel has fully configured its own Alloggiati Web credentials
     * (username + password + WsKey all present), as opposed to relying on the
     * global instance-wide fallback credentials.
     *
     * @return {@code true} if all three fields are non-blank
     */
    public boolean hasAlloggiatiCredentials() {
        return isNotBlank(alloggiatiUsername)
                && isNotBlank(alloggiatiPasswordEncrypted)
                && isNotBlank(alloggiatiWsKeyEncrypted);
    }

    private static boolean isNotBlank(final String value) {
        return value != null && !value.isBlank();
    }
}
