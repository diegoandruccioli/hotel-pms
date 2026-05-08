package com.hotelpms.stay.config;

import com.hotelpms.stay.domain.AlloggiatiComune;
import com.hotelpms.stay.domain.AlloggiatiStato;
import com.hotelpms.stay.domain.AlloggiatiTipdoc;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses CSV data downloaded from the Portale Alloggiati Web lookup endpoints.
 * Uses Apache Commons CSV so that municipality descriptions containing commas
 * (e.g. quoted values like {@code "Fiano, comune di"}) are handled correctly.
 *
 * <p>All methods are stateless and thread-safe.
 */
@Slf4j
public final class AlloggiatiCsvParser {

    /** {@code dd/MM/yyyy HH:mm:ss} — date format used by the Polizia di Stato portal CSV exports. */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Column indices in the portal CSVs (zero-based, after header skip)
    private static final int COL_CODICE = 0;
    private static final int COL_DESCRIZIONE = 1;
    private static final int COL_PROVINCIA = 2;
    // Both STATI and COMUNI have DataFineVal at column index 3:
    // STATI:  Codice, Descrizione, <unknown3>, DataFineVal
    // COMUNI: Codice, Descrizione, Provincia,  DataFineVal
    private static final int COL_DATA_FINE_VAL = 3;

    private static final CSVFormat PORTAL_FORMAT = CSVFormat.DEFAULT
            .withFirstRecordAsHeader()
            .withIgnoreSurroundingSpaces()
            .withIgnoreEmptyLines();

    private AlloggiatiCsvParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses the STATI CSV ({@code Codice,Descrizione,DataFineVal}).
     *
     * @param csv raw CSV content with header on the first row
     * @return list of parsed {@link AlloggiatiStato} entities
     * @throws IOException if reading fails
     */
    public static List<AlloggiatiStato> parseStati(final String csv) throws IOException {
        final List<AlloggiatiStato> result = new ArrayList<>();
        for (final CSVRecord rec : PORTAL_FORMAT.parse(new StringReader(csv))) {
            if (rec.size() < 2) {
                continue;
            }
            final String codice = rec.get(COL_CODICE).trim();
            if (codice.isEmpty()) {
                continue;
            }
            final String descrizione = rec.get(COL_DESCRIZIONE).trim();
            final LocalDate dataFineVal = rec.size() > COL_DATA_FINE_VAL
                    ? parseDate(rec.get(COL_DATA_FINE_VAL).trim()) : null;
            result.add(AlloggiatiStato.builder()
                    .codice(codice)
                    .descrizione(descrizione)
                    .dataFineVal(dataFineVal)
                    .build());
        }
        return result;
    }

    /**
     * Parses the COMUNI CSV ({@code Codice,Descrizione,Provincia,DataFineVal}).
     *
     * @param csv raw CSV content with header on the first row
     * @return list of parsed {@link AlloggiatiComune} entities
     * @throws IOException if reading fails
     */
    public static List<AlloggiatiComune> parseComuni(final String csv) throws IOException {
        final List<AlloggiatiComune> result = new ArrayList<>();
        for (final CSVRecord rec : PORTAL_FORMAT.parse(new StringReader(csv))) {
            if (rec.size() < 3) {
                continue;
            }
            final String codice = rec.get(COL_CODICE).trim();
            final String provincia = rec.get(COL_PROVINCIA).trim();
            if (codice.isEmpty() || provincia.isEmpty()) {
                continue;
            }
            final String descrizione = rec.get(COL_DESCRIZIONE).trim();
            final LocalDate dataFineVal = rec.size() > COL_DATA_FINE_VAL
                    ? parseDate(rec.get(COL_DATA_FINE_VAL).trim()) : null;
            result.add(AlloggiatiComune.builder()
                    .codice(codice)
                    .descrizione(descrizione)
                    .provincia(provincia)
                    .dataFineVal(dataFineVal)
                    .build());
        }
        return result;
    }

    /**
     * Parses the TIPDOC CSV ({@code Codice,Descrizione}).
     *
     * @param csv raw CSV content with header on the first row
     * @return list of parsed {@link AlloggiatiTipdoc} entities
     * @throws IOException if reading fails
     */
    public static List<AlloggiatiTipdoc> parseTipdoc(final String csv) throws IOException {
        final List<AlloggiatiTipdoc> result = new ArrayList<>();
        for (final CSVRecord rec : PORTAL_FORMAT.parse(new StringReader(csv))) {
            if (rec.size() < 2) {
                continue;
            }
            final String codice = rec.get(COL_CODICE).trim();
            if (codice.isEmpty()) {
                continue;
            }
            result.add(AlloggiatiTipdoc.builder()
                    .codice(codice)
                    .descrizione(rec.get(COL_DESCRIZIONE).trim())
                    .build());
        }
        return result;
    }

    /**
     * Parses a date string in {@code dd/MM/yyyy HH:mm:ss} format.
     * Returns {@code null} if the string is blank or cannot be parsed.
     *
     * @param raw the raw date string from the CSV
     * @return parsed {@link LocalDate} or {@code null}
     */
    static LocalDate parseDate(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw, DATE_FMT);
        } catch (final DateTimeParseException ex) {
            log.warn("Could not parse DataFineVal '{}': {}", raw, ex.getMessage());
            return null;
        }
    }
}
