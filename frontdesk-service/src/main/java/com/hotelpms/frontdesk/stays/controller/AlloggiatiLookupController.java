package com.hotelpms.frontdesk.stays.controller;

import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiStato;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiTipdoc;
import com.hotelpms.frontdesk.stays.service.AlloggiatiLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing Portale Alloggiati Web lookup data
 * (stati, comuni, tipdoc) to the frontend.
 *
 * <p>Base path: {@code /api/v1/stays/lookup}
 */
@RestController
@RequestMapping("/api/v1/stays/lookup")
@RequiredArgsConstructor
public class AlloggiatiLookupController {

    private final AlloggiatiLookupService lookupService;

    /**
     * Returns the list of active stati (country codes) for selection in forms.
     *
     * @return HTTP 200 with list of active stati
     */
    @GetMapping("/stati")
    public ResponseEntity<List<AlloggiatiStato>> getStati() {
        return ResponseEntity.ok(lookupService.findAllStati());
    }

    /**
     * Returns comuni for autocomplete or full-list retrieval.
     * When {@code q} is provided the result is limited to 20 entries matching the search term.
     * When only {@code provincia} is provided, returns all active comuni in that province.
     * Both parameters are optional and combinable.
     *
     * @param q         optional substring to search in comune name (case-insensitive)
     * @param provincia optional 2-character province code (e.g. {@code "RM"})
     * @return HTTP 200 with list of matching active comuni
     */
    @GetMapping("/comuni")
    public ResponseEntity<List<AlloggiatiComune>> getComuni(
            @RequestParam(name = "q", required = false) final String q,
            @RequestParam(name = "provincia", required = false) final String provincia) {
        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(lookupService.searchComuni(q, provincia));
        }
        if (provincia != null && !provincia.isBlank()) {
            return ResponseEntity.ok(lookupService.findComuniByProvincia(provincia));
        }
        return ResponseEntity.ok(lookupService.findAllComuni());
    }

    /**
     * Returns the full list of document-type codes (tipdoc).
     *
     * @return HTTP 200 with list of all tipdoc
     */
    @GetMapping("/tipdoc")
    public ResponseEntity<List<AlloggiatiTipdoc>> getTipdoc() {
        return ResponseEntity.ok(lookupService.findAllTipdoc());
    }
}
