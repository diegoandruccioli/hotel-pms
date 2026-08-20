package com.hotelpms.frontdesk.citytax.controller;

import com.hotelpms.frontdesk.citytax.dto.CityTaxRateRequest;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateResponse;
import com.hotelpms.frontdesk.citytax.service.CityTaxRateAdminService;
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
 * Controller for a hotel's tourist-tax rate rules. Append-only — only
 * {@code GET}/{@code POST}, no update/delete (see {@code CityTaxRate} javadoc).
 * Write operations restricted to ADMIN/OWNER; all operations scoped to the
 * caller's own hotel, resolved from the security context, never from the request.
 */
@RestController
@RequestMapping("/api/v1/stays/city-tax-rates")
@RequiredArgsConstructor
public class CityTaxRateController {

    private final CityTaxRateAdminService cityTaxRateAdminService;

    /**
     * Lists every rate rule for the caller's hotel, newest {@code validFrom} first.
     *
     * @return the rules
     */
    @GetMapping
    public ResponseEntity<List<CityTaxRateResponse>> listRules() {
        return ResponseEntity.ok(cityTaxRateAdminService.listRules(resolveHotelId()));
    }

    /**
     * Creates a rate rule for the caller's hotel. Restricted to ADMIN/OWNER.
     *
     * @param request the rule details
     * @return the created rule
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<CityTaxRateResponse> createRule(@NonNull @Valid @RequestBody final CityTaxRateRequest request) {
        final CityTaxRateResponse response = cityTaxRateAdminService.createRule(resolveHotelId(), request);
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
