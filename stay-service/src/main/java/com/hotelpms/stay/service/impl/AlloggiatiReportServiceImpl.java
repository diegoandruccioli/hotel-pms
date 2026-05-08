package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.domain.AlloggiatiComune;
import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.domain.StayGuest;
import com.hotelpms.stay.domain.TravellerType;
import com.hotelpms.stay.dto.AlloggiatiRowDto;
import com.hotelpms.stay.exception.AlloggiatiRowLimitExceededException;
import com.hotelpms.stay.exception.AlloggiatiValidationException;
import com.hotelpms.stay.repository.StayRepository;
import com.hotelpms.stay.service.AlloggiatiLookupService;
import com.hotelpms.stay.service.AlloggiatiReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Generates the Italian Alloggiati Web police report (tracciato 168 caratteri).
 *
 * <p>File format: each record is exactly 168 characters wide.
 * Records are separated by CR+LF; the final record has no trailing CR+LF
 * (conformant to the Portale Alloggiati Web upload specification).
 * A single file must not exceed {@value #MAX_ROWS_PER_FILE} records;
 * {@link AlloggiatiRowLimitExceededException} is thrown if that ceiling is breached.
 *
 * <p>Guest data is sourced from {@link StayGuest} records (not the guest-service profile),
 * because Alloggiati fields (9-char codes for stato/comune, tipdoc codes, etc.) are
 * captured at check-in time from the official portale lookup tables.
 *
 * <p>Within each stay the ordering is: CAPOFAMIGLIA/CAPOGRUPPO first (TIPALLOG 17/18),
 * then FAMILIARE/MEMBRO_GRUPPO (19/20), then OSPITE_SINGOLO (16).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlloggiatiReportServiceImpl implements AlloggiatiReportService {

    /** Maximum rows allowed in a single Portale Alloggiati Web upload file. */
    static final int MAX_ROWS_PER_FILE = 1000;

    private static final String CODICE_ITALIA = "100000100";
    private static final String CRLF = "\r\n";
    private static final int MAX_PERMANENZA = 30;
    private static final int DEFAULT_PERMANENZA = 1;

    // Fixed-width field lengths from the official tracciato
    private static final int LEN_TIPO_ALLOGGIATO = 2;
    private static final int LEN_DATA_ARRIVO = 10;
    private static final int LEN_PERMANENZA = 2;
    private static final int LEN_COGNOME = 50;
    private static final int LEN_NOME = 30;
    private static final int LEN_SESSO = 1;
    private static final int LEN_DATA_NASCITA = 10;
    private static final int LEN_COMUNE_NASCITA = 9;
    private static final int LEN_PROVINCIA_NASCITA = 2;
    private static final int LEN_STATO_NASCITA = 9;
    private static final int LEN_CITTADINANZA = 9;
    private static final int LEN_TIPO_DOC = 5;
    private static final int LEN_NUMERO_DOC = 20;
    private static final int LEN_LUOGO_RILASCIO = 9;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StayRepository stayRepository;
    private final AlloggiatiLookupService lookupService;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public String generateReport(final LocalDate date) {
        log.info("Generating Alloggiati Web report (168-char tracciato) for date: {}", date);
        final List<AlloggiatiRowDto> rows = buildRows(date);
        log.info("Built {} guest rows for report on {}", rows.size(), date);

        if (rows.size() > MAX_ROWS_PER_FILE) {
            throw new AlloggiatiRowLimitExceededException(
                    "ALLOGGIATI_ROW_LIMIT_EXCEEDED: " + rows.size()
                    + " rows exceed the " + MAX_ROWS_PER_FILE + "-row limit per file");
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            sb.append(toFixedWidthRecord(rows.get(i)));
            if (i < rows.size() - 1) {
                sb.append(CRLF);
            }
        }
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiRowDto> generateJsonReport(final LocalDate date) {
        log.info("Generating Alloggiati JSON export for date: {}", date);
        final List<AlloggiatiRowDto> rows = buildRows(date);
        log.info("Built {} guest rows for JSON export on {}", rows.size(), date);
        return rows;
    }

    // -----------------------------------------------------------------------
    // Core row-building logic
    // -----------------------------------------------------------------------

    private List<AlloggiatiRowDto> buildRows(final LocalDate date) {
        final LocalDateTime start = date.atStartOfDay();
        final LocalDateTime end = date.plusDays(1).atStartOfDay();
        final List<Stay> checkIns = stayRepository.findByActualCheckInTimeBetween(start, end);
        final List<AlloggiatiRowDto> rows = new ArrayList<>();

        for (final Stay stay : checkIns) {
            final List<StayGuest> guests = stay.getGuests();
            if (guests == null || guests.isEmpty()) {
                log.warn("[REPORT] No StayGuest records for stayId={} — skipping", stay.getId());
                continue;
            }
            validateGroupCoherence(stay);
            validateDates(stay, date);

            final int permanenza = computePermanenza(date, stay.getExpectedCheckOutDate());

            guests.stream()
                    .sorted(Comparator.comparingInt(g -> travellerOrder(g.getTravellerType())))
                    .map(guest -> {
                        warnOnInvalidLookupCodes(guest);
                        return buildRow(guest, date, permanenza);
                    })
                    .forEach(rows::add);
        }
        return rows;
    }

    private AlloggiatiRowDto buildRow(final StayGuest g, final LocalDate arrivalDate, final int permanenza) {
        final TravellerType type = g.getTravellerType() != null
                ? g.getTravellerType()
                : TravellerType.OSPITE_SINGOLO;
        final boolean hasDoc = type != TravellerType.FAMILIARE && type != TravellerType.MEMBRO_GRUPPO;

        final String sesso = mapSesso(g.getGender());
        final BirthplaceInfo birth = resolveBirthplace(g.getPlaceOfBirth());
        final String luogoRilascio = hasDoc ? nullToEmpty(g.getDocumentPlaceOfIssue()) : "";

        return new AlloggiatiRowDto(
                type.portalCode(),
                arrivalDate.format(DATE_FMT),
                permanenza,
                g.getLastName(),
                g.getFirstName(),
                sesso,
                g.getDateOfBirth() != null ? g.getDateOfBirth().format(DATE_FMT) : "",
                birth.comune(),
                birth.provincia(),
                birth.stato(),
                resolveStatoCode(g.getCitizenship()),
                hasDoc ? nullToEmpty(g.getDocumentType()) : "",
                hasDoc ? nullToEmpty(g.getDocumentNumber()) : "",
                luogoRilascio);
    }

    // -----------------------------------------------------------------------
    // Domain validations
    // -----------------------------------------------------------------------

    /**
     * Validates group coherence rules for a single stay.
     * Rules: FAMILIARE requires exactly one CAPOFAMIGLIA; MEMBRO_GRUPPO requires exactly one
     * CAPOGRUPPO; at most one CAPOFAMIGLIA and one CAPOGRUPPO are allowed per stay.
     *
     * @param stay the stay to validate
     * @throws AlloggiatiValidationException if any coherence rule is violated
     */
    private static void validateGroupCoherence(final Stay stay) {
        final List<StayGuest> guests = stay.getGuests();
        if (guests == null || guests.isEmpty()) {
            return;
        }

        final long capoFamCount = count(guests, TravellerType.CAPOFAMIGLIA);
        final long capoGrupCount = count(guests, TravellerType.CAPOGRUPPO);
        final long familiareCount = count(guests, TravellerType.FAMILIARE);
        final long membroCount = count(guests, TravellerType.MEMBRO_GRUPPO);

        if (familiareCount > 0 && capoFamCount == 0) {
            throw new AlloggiatiValidationException(
                    "ALLOGGIATI_FAMILIARE_WITHOUT_CAPO: stayId=" + stay.getId());
        }
        if (membroCount > 0 && capoGrupCount == 0) {
            throw new AlloggiatiValidationException(
                    "ALLOGGIATI_MEMBRO_WITHOUT_CAPO: stayId=" + stay.getId());
        }
        if (capoFamCount > 1) {
            throw new AlloggiatiValidationException(
                    "ALLOGGIATI_MULTIPLE_CAPOFAMIGLIA: stayId=" + stay.getId());
        }
        if (capoGrupCount > 1) {
            throw new AlloggiatiValidationException(
                    "ALLOGGIATI_MULTIPLE_CAPOGRUPPO: stayId=" + stay.getId());
        }
    }

    /**
     * Validates that the expected check-out date is not before the arrival date.
     *
     * @param stay        the stay to validate
     * @param arrivalDate the check-in date being reported
     * @throws AlloggiatiValidationException if checkOut precedes arrivalDate
     */
    private static void validateDates(final Stay stay, final LocalDate arrivalDate) {
        final LocalDate checkOut = stay.getExpectedCheckOutDate();
        if (checkOut != null && checkOut.isBefore(arrivalDate)) {
            throw new AlloggiatiValidationException(
                    "ALLOGGIATI_INVALID_DATES: checkOut=" + checkOut
                    + " is before arrival=" + arrivalDate + " stayId=" + stay.getId());
        }
    }

    /**
     * Logs a warning when a guest record references codes not present in the lookup tables.
     * This is a soft check: the report is still generated (the portal rejects unknown codes
     * at upload time with its own error response).
     *
     * @param guest the guest to check
     */
    private void warnOnInvalidLookupCodes(final StayGuest guest) {
        if (guest.getCitizenship() != null && !guest.getCitizenship().isBlank()
                && lookupService.findStatoByCodice(guest.getCitizenship()).isEmpty()) {
            log.warn("[REPORT] Unknown citizenship code '{}' for stayGuestId={}",
                    guest.getCitizenship(), guest.getId());
        }
        if (guest.getDocumentType() != null && !guest.getDocumentType().isBlank()
                && lookupService.findTipdocByCodice(guest.getDocumentType()).isEmpty()) {
            log.warn("[REPORT] Unknown documentType code '{}' for stayGuestId={}",
                    guest.getDocumentType(), guest.getId());
        }
        if (guest.getPlaceOfBirth() != null && !guest.getPlaceOfBirth().isBlank()) {
            final boolean isComuneCode = lookupService.findComuneByCodice(guest.getPlaceOfBirth()).isPresent();
            final boolean isStatoCode = lookupService.findStatoByCodice(guest.getPlaceOfBirth()).isPresent();
            if (!isComuneCode && !isStatoCode) {
                log.warn("[REPORT] placeOfBirth '{}' not found in comuni or stati tables for stayGuestId={}",
                        guest.getPlaceOfBirth(), guest.getId());
            }
        }
    }

    // -----------------------------------------------------------------------
    // 168-char fixed-width serialisation
    // -----------------------------------------------------------------------

    private String toFixedWidthRecord(final AlloggiatiRowDto r) {
        return pad(r.tipoAlloggiato(), LEN_TIPO_ALLOGGIATO)
                + pad(r.dataArrivo(), LEN_DATA_ARRIVO)
                + padNum(r.permanenza(), LEN_PERMANENZA)
                + pad(r.cognome(), LEN_COGNOME)
                + pad(r.nome(), LEN_NOME)
                + pad(r.sesso(), LEN_SESSO)
                + pad(r.dataNascita(), LEN_DATA_NASCITA)
                + pad(r.comuneNascita(), LEN_COMUNE_NASCITA)
                + pad(r.provinciaNascita(), LEN_PROVINCIA_NASCITA)
                + pad(r.statoNascita(), LEN_STATO_NASCITA)
                + pad(r.cittadinanza(), LEN_CITTADINANZA)
                + pad(r.tipoDocumento(), LEN_TIPO_DOC)
                + pad(r.numeroDocumento(), LEN_NUMERO_DOC)
                + pad(r.luogoRilascioDoc(), LEN_LUOGO_RILASCIO);
    }

    /**
     * Left-aligns {@code s} and pads to {@code len} with spaces; truncates if longer.
     *
     * @param s   the string to pad (may be null)
     * @param len the target length
     * @return the padded or truncated string
     */
    private static String pad(final String s, final int len) {
        final String safe = s == null ? "" : s;
        if (safe.length() >= len) {
            return safe.substring(0, len);
        }
        return String.format("%-" + len + "s", safe);
    }

    /**
     * Right-pads a non-negative integer to {@code len} digits with leading zeros;
     * clamps to {@value #MAX_PERMANENZA} to enforce the portal maximum.
     *
     * @param n   the number to format
     * @param len the target length
     * @return the zero-padded number string
     */
    private static String padNum(final int n, final int len) {
        final int clamped = Math.min(n, MAX_PERMANENZA);
        return String.format("%0" + len + "d", clamped);
    }

    // -----------------------------------------------------------------------
    // Birthplace resolution
    // -----------------------------------------------------------------------

    /**
     * Resolves a raw birthplace code to a {@link BirthplaceInfo}.
     * If the code matches an active comune, the guest was born in Italy.
     * Otherwise the code is assumed to be a foreign stato code.
     *
     * @param codice the 9-char comune or stato code stored on the StayGuest
     * @return resolved birthplace info (never null)
     */
    private BirthplaceInfo resolveBirthplace(final String codice) {
        if (codice == null || codice.isBlank()) {
            return new BirthplaceInfo("", "", "");
        }
        final Optional<AlloggiatiComune> comune = lookupService.findComuneByCodice(codice);
        if (comune.isPresent()) {
            final AlloggiatiComune c = comune.get();
            if (c.getDataFineVal() != null && !c.getDataFineVal().isAfter(LocalDate.now())) {
                log.warn("[REPORT] Comune '{}' ({}) is expired (dataFineVal={})",
                        c.getDescrizione(), codice, c.getDataFineVal());
            }
            return new BirthplaceInfo(codice, c.getProvincia(), CODICE_ITALIA);
        }
        return new BirthplaceInfo("", "", codice);
    }

    /**
     * Returns the stato code or empty string when null.
     *
     * @param codice the 9-char stato code
     * @return the code, or empty string
     */
    private static String resolveStatoCode(final String codice) {
        return codice != null ? codice : "";
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static long count(final List<StayGuest> guests, final TravellerType type) {
        return guests.stream().filter(g -> type == g.getTravellerType()).count();
    }

    private static String mapSesso(final String gender) {
        if (gender == null) {
            return "1";
        }
        final String g = gender.trim().toUpperCase(Locale.ROOT);
        return switch (g) {
            case "2", "F", "FEMMINA", "FEMALE" -> "2";
            default -> "1";
        };
    }

    private static int computePermanenza(final LocalDate arrivalDate, final LocalDate expectedCheckOut) {
        if (expectedCheckOut == null || !expectedCheckOut.isAfter(arrivalDate)) {
            return DEFAULT_PERMANENZA;
        }
        final long nights = ChronoUnit.DAYS.between(arrivalDate, expectedCheckOut);
        return (int) Math.min(nights, MAX_PERMANENZA);
    }

    private static int travellerOrder(final TravellerType type) {
        return type != null ? type.numericCode() : TravellerType.OSPITE_SINGOLO.numericCode();
    }

    private static String nullToEmpty(final String s) {
        return s != null ? s : "";
    }

    // -----------------------------------------------------------------------
    // Inner types — must be declared after all methods per Checkstyle ordering
    // -----------------------------------------------------------------------

    private record BirthplaceInfo(String comune, String provincia, String stato) { }
}
