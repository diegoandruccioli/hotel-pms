package com.hotelpms.frontdesk.stays.config;

import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiStato;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiTipdoc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AlloggiatiCsvParser}.
 * Uses CSV strings in the exact format produced by the Portale Alloggiati Web
 * download endpoints (Codice, Descrizione, [Provincia,] DataFineVal).
 */
class AlloggiatiCsvParserTest {

    private static final String CRLF = "\r\n";
    // Both STATI and COMUNI headers have 4 columns; DataFineVal is always at index 3
    private static final String STATI_HEADER = "Codice,Descrizione,Extra,DataFineVal" + CRLF;
    private static final String COMUNI_HEADER = "Codice,Descrizione,Provincia,DataFineVal" + CRLF;
    private static final String TIPDOC_HEADER = "Codice,Descrizione" + CRLF;

    private static final String CODICE_ITALIA = "100000100";
    private static final String CODICE_GERMANIA = "100000083";
    private static final String CODICE_ROMA = "058091000";
    private static final String DESC_ROMA = "Roma";
    private static final String PROVINCIA_RM = "RM";
    private static final String CODICE_PASOR = "PASOR";
    private static final String DESC_PASOR = "PASSAPORTO ORDINARIO";

    private static final int YEAR_1990 = 1990;
    private static final int YEAR_1999 = 1999;
    private static final int YEAR_DATE_TEST = 2026;
    private static final int MONTH_6 = 6;
    private static final int MONTH_12 = 12;
    private static final int MONTH_APR = 4;
    private static final int DAY_1 = 1;
    private static final int DAY_15 = 15;
    private static final int DAY_31 = 31;

    // -----------------------------------------------------------------------
    // parseStati
    // -----------------------------------------------------------------------

    @Test
    void shouldParseStatiWithValidRecord() throws IOException {
        final String csv = STATI_HEADER
                + CODICE_ITALIA + ",ITALIA,," + CRLF
                + CODICE_GERMANIA + ",GERMANIA,," + CRLF;

        final List<AlloggiatiStato> result = AlloggiatiCsvParser.parseStati(csv);

        assertEquals(2, result.size());
        assertEquals(CODICE_ITALIA, result.get(0).getCodice());
        assertEquals("ITALIA", result.get(0).getDescrizione());
        assertNull(result.get(0).getDataFineVal(), "Empty dataFineVal must be null");
    }

    @Test
    void shouldParseStatiDataFineVal() throws IOException {
        final String csv = STATI_HEADER
                + "100000999,PAESE_CESSATO,,01/06/1990 00:00:00" + CRLF;

        final List<AlloggiatiStato> result = AlloggiatiCsvParser.parseStati(csv);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(YEAR_1990, MONTH_6, DAY_1), result.get(0).getDataFineVal());
    }

    @Test
    void shouldSkipStatiRowWithEmptyCodice() throws IOException {
        final String csv = STATI_HEADER
                + ",SENZA_CODICE,," + CRLF
                + CODICE_ITALIA + ",ITALIA,," + CRLF;

        final List<AlloggiatiStato> result = AlloggiatiCsvParser.parseStati(csv);

        assertEquals(1, result.size());
        assertEquals(CODICE_ITALIA, result.get(0).getCodice());
    }

    @Test
    void shouldReturnEmptyListForStatiHeaderOnly() throws IOException {
        final List<AlloggiatiStato> result = AlloggiatiCsvParser.parseStati(STATI_HEADER);

        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // parseComuni
    // -----------------------------------------------------------------------

    @Test
    void shouldParseComuniWithValidRecord() throws IOException {
        final String csv = COMUNI_HEADER
                + CODICE_ROMA + "," + DESC_ROMA + "," + PROVINCIA_RM + "," + CRLF
                + "015146000,Milano,MI," + CRLF;

        final List<AlloggiatiComune> result = AlloggiatiCsvParser.parseComuni(csv);

        assertEquals(2, result.size());
        assertEquals(CODICE_ROMA, result.get(0).getCodice());
        assertEquals(DESC_ROMA, result.get(0).getDescrizione());
        assertEquals(PROVINCIA_RM, result.get(0).getProvincia());
        assertNull(result.get(0).getDataFineVal());
    }

    @Test
    void shouldParseComuniWithQuotedDescriptionContainingComma() throws IOException {
        // Some comuni have commas in the official name (e.g. "Fiano Romano, comune di")
        final String csv = COMUNI_HEADER
                + "\"058044000\",\"Fiano Romano, comune di\"," + PROVINCIA_RM + "," + CRLF;

        final List<AlloggiatiComune> result = AlloggiatiCsvParser.parseComuni(csv);

        assertEquals(1, result.size());
        assertEquals("058044000", result.get(0).getCodice());
        assertEquals("Fiano Romano, comune di", result.get(0).getDescrizione());
        assertEquals(PROVINCIA_RM, result.get(0).getProvincia());
    }

    @Test
    void shouldParseComuniWithCessatoDate() throws IOException {
        final String csv = COMUNI_HEADER
                + "058099000,VecchioComune," + PROVINCIA_RM + ",31/12/1999 00:00:00" + CRLF;

        final List<AlloggiatiComune> result = AlloggiatiCsvParser.parseComuni(csv);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(YEAR_1999, MONTH_12, DAY_31), result.get(0).getDataFineVal());
    }

    @Test
    void shouldSkipComuneRowWithEmptyProvincia() throws IOException {
        final String csv = COMUNI_HEADER
                + "999999999,SenzaProvincia,," + CRLF
                + CODICE_ROMA + "," + DESC_ROMA + "," + PROVINCIA_RM + "," + CRLF;

        final List<AlloggiatiComune> result = AlloggiatiCsvParser.parseComuni(csv);

        assertEquals(1, result.size());
        assertEquals(CODICE_ROMA, result.get(0).getCodice());
    }

    @Test
    void shouldHandleComuniWithWindowsLineEndings() throws IOException {
        final String csv = COMUNI_HEADER
                + CODICE_ROMA + "," + DESC_ROMA + "," + PROVINCIA_RM + "," + CRLF
                + "015146000,Milano,MI," + CRLF;

        final List<AlloggiatiComune> result = AlloggiatiCsvParser.parseComuni(csv);

        assertEquals(2, result.size());
    }

    // -----------------------------------------------------------------------
    // parseTipdoc
    // -----------------------------------------------------------------------

    @Test
    void shouldParseTipdocWithValidRecord() throws IOException {
        final String csv = TIPDOC_HEADER
                + CODICE_PASOR + "," + DESC_PASOR + CRLF
                + "CARTE,CARTA D'IDENTITA'" + CRLF;

        final List<AlloggiatiTipdoc> result = AlloggiatiCsvParser.parseTipdoc(csv);

        assertEquals(2, result.size());
        assertEquals(CODICE_PASOR, result.get(0).getCodice());
        assertEquals(DESC_PASOR, result.get(0).getDescrizione());
        assertEquals("CARTE", result.get(1).getCodice());
    }

    @Test
    void shouldSkipTipdocRowWithEmptyCodice() throws IOException {
        final String csv = TIPDOC_HEADER
                + ",NESSUN_CODICE" + CRLF
                + CODICE_PASOR + "," + DESC_PASOR + CRLF;

        final List<AlloggiatiTipdoc> result = AlloggiatiCsvParser.parseTipdoc(csv);

        assertEquals(1, result.size());
        assertEquals(CODICE_PASOR, result.get(0).getCodice());
    }

    // -----------------------------------------------------------------------
    // parseDate
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnNullForBlankDate() {
        assertNull(AlloggiatiCsvParser.parseDate(""));
        assertNull(AlloggiatiCsvParser.parseDate("   "));
        assertNull(AlloggiatiCsvParser.parseDate(null));
    }

    @Test
    void shouldReturnNullForUnparsableDate() {
        assertNull(AlloggiatiCsvParser.parseDate("not-a-date"));
    }

    @Test
    void shouldParseDateCorrectly() {
        final LocalDate result = AlloggiatiCsvParser.parseDate("15/04/2026 14:30:00");

        assertEquals(LocalDate.of(YEAR_DATE_TEST, MONTH_APR, DAY_15), result);
    }
}
