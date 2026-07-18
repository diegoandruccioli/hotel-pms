package com.hotelpms.billing.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Minimal view of Spring Data's {@code Page<GuestResponse>} JSON shape returned by
 * guest-service's {@code GET /search} endpoint. Only {@code content} is needed here
 * (guest name/email resolution for invoice search); the rest of the Page envelope
 * (totalPages, sort, etc.) is intentionally ignored.
 *
 * @param content the matching guests on the requested page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GuestSearchPageResponse(List<GuestResponse> content) {

    /**
     * Defensive copy of the content list.
     *
     * @param content the matching guests on the requested page
     */
    public GuestSearchPageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
