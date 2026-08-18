package com.hotelpms.frontdesk.citytax.controller;

import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryRequest;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryResponse;
import com.hotelpms.frontdesk.citytax.service.HotelCategoryHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for a hotel's classification/category history. Append-only —
 * only {@code GET}/{@code POST}, no update/delete (see {@code HotelCategoryHistory}
 * javadoc). Write operations restricted to ADMIN/OWNER; all operations scoped
 * to the caller's own hotel, resolved from the security context.
 */
@RestController
@RequestMapping("/api/v1/stays/hotel-category")
@RequiredArgsConstructor
public class HotelCategoryHistoryController {

    private final HotelCategoryHistoryService hotelCategoryHistoryService;

    /**
     * Lists the caller's hotel's full category history, newest first.
     *
     * @return the history
     */
    @GetMapping
    public ResponseEntity<List<HotelCategoryHistoryResponse>> listHistory() {
        return ResponseEntity.ok(hotelCategoryHistoryService.listHistory(resolveHotelId()));
    }

    /**
     * Records a new category for the caller's hotel. Restricted to ADMIN/OWNER.
     *
     * @param request the category details
     * @return the created entry
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<HotelCategoryHistoryResponse> recordCategory(
            @NonNull @Valid @RequestBody final HotelCategoryHistoryRequest request) {
        final HotelCategoryHistoryResponse response =
                hotelCategoryHistoryService.recordCategory(resolveHotelId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Extracts the hotel UUID from the current security context details.
     *
     * @return the hotel UUID of the authenticated user
     */
    private UUID resolveHotelId() {
        final Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return UUID.fromString(String.valueOf(details));
    }
}
