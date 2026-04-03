package com.hotelpms.guest.dto.request;

import com.hotelpms.guest.util.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for guest creation and update operations.
 *
 * @param firstName   The first name of the guest.
 * @param lastName    The last name of the guest.
 * @param email       The email address of the guest.
 * @param phone       The phone number of the guest.
 * @param address     The street address of the guest.
 * @param city        The city of the guest.
 * @param country     The country of the guest.
 * @param dateOfBirth The date of birth of the guest.
 */
public record GuestRequest(
        @NotBlank @Size(max = ValidationConstants.MAX_FIRST_NAME_LENGTH) String firstName,
        @NotBlank @Size(max = ValidationConstants.MAX_LAST_NAME_LENGTH) String lastName,
        @NotBlank @Email @Size(max = ValidationConstants.MAX_EMAIL_LENGTH) String email,
        @Size(max = ValidationConstants.MAX_PHONE_LENGTH) String phone,
        @Size(max = ValidationConstants.MAX_ADDRESS_LENGTH) String address,
        @Size(max = ValidationConstants.MAX_LOCATION_LENGTH) String city,
        @Size(max = ValidationConstants.MAX_LOCATION_LENGTH) String country,
        LocalDate dateOfBirth) {
}
