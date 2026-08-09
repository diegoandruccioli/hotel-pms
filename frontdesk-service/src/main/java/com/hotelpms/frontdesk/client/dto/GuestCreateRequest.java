package com.hotelpms.frontdesk.client.dto;

/**
 * Minimal request body to create a guest profile from a quotation prospect at
 * conversion time. guest-service's {@code GuestRequest} accepts more optional
 * fields — only the ones a quotation prospect actually carries are sent.
 *
 * @param firstName the prospect's first name
 * @param lastName  the prospect's last name
 * @param email     the prospect's email
 */
public record GuestCreateRequest(String firstName, String lastName, String email) {
}
