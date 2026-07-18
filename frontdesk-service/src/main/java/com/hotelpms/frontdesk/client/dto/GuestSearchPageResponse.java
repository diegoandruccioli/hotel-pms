package com.hotelpms.frontdesk.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Minimal view of Spring Data's {@code Page<GuestResponse>} JSON shape returned by
 * guest-service's {@code GET /search} endpoint. Only {@code content} is needed here
 * (guest name resolution for reservation search, C12); the rest of the Page envelope
 * is intentionally ignored.
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
