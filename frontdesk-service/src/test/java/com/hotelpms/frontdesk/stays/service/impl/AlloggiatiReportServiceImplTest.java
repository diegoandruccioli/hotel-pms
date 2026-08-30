package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.domain.TravellerType;
import com.hotelpms.frontdesk.stays.dto.AlloggiatiRowDto;
import com.hotelpms.frontdesk.exception.AlloggiatiRowLimitExceededException;
import com.hotelpms.frontdesk.stays.repository.StayGuestRepository;
import com.hotelpms.frontdesk.stays.service.AlloggiatiLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlloggiatiReportServiceImplTest {

    // -----------------------------------------------------------------------
    // Alloggiati portal codes used in test data
    // -----------------------------------------------------------------------
    private static final String CODICE_ITALIA = "100000100";
    private static final String SKIPPED_NOT_THROWN_MSG = "the only stay is invalid and must be skipped, not thrown";
    private static final String CODICE_ROMA = "058091000";
    private static final String CODICE_GERMANIA = "100000083";
    private static final String CODICE_FRANCIA = "100000070";
    private static final String PROVINCIA_ROMA = "RM";
    private static final String CODICE_TIPDOC_PASS = "PASOR";
    private static final String DOC_NUMBER = "AA1234567";
    private static final String SURNAME_ROSSI = "Rossi";
    private static final String DESC_ROMA = "Roma";
    private static final String CRLF = "\r\n";
    private static final String BLANK_9 = "         "; // 9 spaces — blank fixed-width field
    private static final String BLANK_2 = "  ";        // 2 spaces — blank province field
    private static final String TIPALLOG_16 = "16";    // portal code for OSPITE_SINGOLO
    private static final String TIPALLOG_19 = "19";    // portal code for FAMILIARE
    private static final String TIPALLOG_20 = "20";    // portal code for MEMBRO_GRUPPO
    private static final String GENDER_MALE = "M";

    // Numeric constants for test data construction
    private static final int LEN_COGNOME_MAX = 50;
    private static final int LEN_SURNAME_LONG = 60; // intentionally > LEN_COGNOME_MAX to test truncation
    private static final int YEAR_DOB_OFFSET_5 = 5; // used to create a child of the family head
    private static final int NIGHTS_OVER_LIMIT = 60; // >30 to test permanenza clamp

    // Date constants
    private static final int YEAR = 2026;
    private static final int MONTH = 4;
    private static final int DAY = 15;
    private static final int YEAR_DOB = 1985;
    private static final int MONTH_DOB = 5;
    private static final int DAY_DOB = 20;
    private static final int CHECKIN_HOUR = 14;
    private static final int NIGHTS_TO_CHECKOUT = 3;

    // Tracciato 168-char offset constants (zero-based, end is exclusive)
    private static final int EXPECTED_RECORD_LEN = 168;
    private static final int POS_TIPALLOG_START = 0;
    private static final int POS_TIPALLOG_END = 2;
    private static final int POS_PERMANENZA_START = 12;
    private static final int POS_PERMANENZA_END = 14;
    private static final int POS_COGNOME_START = 14;
    private static final int POS_COGNOME_END = 64;
    private static final int POS_SESSO_START = 94;
    private static final int POS_SESSO_END = 95;
    private static final int POS_COMUNE_NASCITA_START = 105;
    private static final int POS_COMUNE_NASCITA_END = 114;
    private static final int POS_PROVINCIA_NASCITA_START = 114;
    private static final int POS_PROVINCIA_NASCITA_END = 116;
    private static final int POS_STATO_NASCITA_START = 116;
    private static final int POS_STATO_NASCITA_END = 125;
    private static final int POS_TIPO_DOC_START = 134;
    private static final int POS_TIPO_DOC_END = 139;
    private static final int POS_NUMERO_DOC_START = 139;
    private static final int POS_NUMERO_DOC_END = 159;
    private static final int POS_LUOGO_RILASCIO_START = 159;
    private static final int POS_LUOGO_RILASCIO_END = 168;

    @Mock
    private StayGuestRepository stayGuestRepository;

    @Mock
    private AlloggiatiLookupService lookupService;

    @InjectMocks
    private AlloggiatiReportServiceImpl service;

    private LocalDate reportDate;
    private LocalDate checkOutDate;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        reportDate = LocalDate.of(YEAR, MONTH, DAY);
        checkOutDate = reportDate.plusDays(NIGHTS_TO_CHECKOUT);
        hotelId = UUID.randomUUID();
    }

    // -----------------------------------------------------------------------
    // Helper factories
    // -----------------------------------------------------------------------

    private Stay stayWith(final StayGuest... guests) {
        final Stay stay = Stay.builder()
                .id(UUID.randomUUID())
                .guestId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.of(YEAR, MONTH, DAY, CHECKIN_HOUR, 0))
                .expectedCheckOutDate(checkOutDate)
                .build();
        stay.getGuests().addAll(List.of(guests));
        for (final StayGuest guest : guests) {
            guest.setStay(stay);
        }
        return stay;
    }

    /**
     * Stubs {@code stayGuestRepository.findByHotelIdAndArrivalDate} to return every guest
     * of the given stays — the Parte 1 selection this service now queries by, replacing
     * the old check-in-date-based stay lookup. Each guest defaults {@code arrivalDate} to
     * its stay's check-in date (the pre-Parte-1 behavior every existing test assumes)
     * unless the test already set one explicitly.
     *
     * @param stays the stays whose guests should be selected for {@link #reportDate}
     */
    private void stubArrivals(final List<Stay> stays) {
        final List<StayGuest> guests = new ArrayList<>();
        for (final Stay stay : stays) {
            for (final StayGuest guest : stay.getGuests()) {
                guest.setStay(stay);
                if (guest.getArrivalDate() == null) {
                    guest.setArrivalDate(stay.getActualCheckInTime() != null
                            ? stay.getActualCheckInTime().toLocalDate() : reportDate);
                }
                guests.add(guest);
            }
        }
        when(stayGuestRepository.findByHotelIdAndArrivalDate(any(), any())).thenReturn(guests);
    }

    private StayGuest guestItalian(final TravellerType type, final boolean primary) {
        return StayGuest.builder()
                .id(UUID.randomUUID())
                .firstName("Mario")
                .lastName(SURNAME_ROSSI)
                .gender(GENDER_MALE)
                .dateOfBirth(LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_ROMA)
                .citizenship(CODICE_ITALIA)
                .documentType(CODICE_TIPDOC_PASS)
                .documentNumber(DOC_NUMBER)
                .documentPlaceOfIssue(CODICE_ROMA)
                .isPrimaryGuest(primary)
                .travellerType(type)
                .build();
    }

    private StayGuest guestForeign(final TravellerType type) {
        return StayGuest.builder()
                .id(UUID.randomUUID())
                .firstName("Hans")
                .lastName("Mueller")
                .gender(GENDER_MALE)
                .dateOfBirth(LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_GERMANIA)
                .citizenship(CODICE_GERMANIA)
                .documentType(CODICE_TIPDOC_PASS)
                .documentNumber("DE9876543")
                .documentPlaceOfIssue(CODICE_GERMANIA)
                .isPrimaryGuest(type == TravellerType.OSPITE_SINGOLO)
                .travellerType(type)
                .build();
    }

    private StayGuest guestFamiliare() {
        return StayGuest.builder()
                .id(UUID.randomUUID())
                .firstName("Laura")
                .lastName(SURNAME_ROSSI)
                .gender("F")
                .dateOfBirth(LocalDate.of(YEAR_DOB + 2, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_ROMA)
                .citizenship(CODICE_ITALIA)
                .documentType(null)
                .documentNumber(null)
                .documentPlaceOfIssue(null)
                .isPrimaryGuest(false)
                .travellerType(TravellerType.FAMILIARE)
                .build();
    }

    private StayGuest guestMembroGruppo() {
        return StayGuest.builder()
                .id(UUID.randomUUID())
                .firstName("Giulia")
                .lastName("Bianchi")
                .gender("F")
                .dateOfBirth(LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_ROMA)
                .citizenship(CODICE_ITALIA)
                .documentType(null)
                .documentNumber(null)
                .documentPlaceOfIssue(null)
                .isPrimaryGuest(false)
                .travellerType(TravellerType.MEMBRO_GRUPPO)
                .build();
    }

    private AlloggiatiComune comuneRoma() {
        return AlloggiatiComune.builder()
                .codice(CODICE_ROMA).descrizione(DESC_ROMA).provincia(PROVINCIA_ROMA).build();
    }

    // -----------------------------------------------------------------------
    // Basic 168-char conformance
    // -----------------------------------------------------------------------

    @Test
    void shouldGenerateSingleOspiteRecord168Chars() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertNotNull(report);
        assertEquals(EXPECTED_RECORD_LEN, report.length(),
                "Single record must be exactly 168 characters with no trailing CRLF");
        assertTrue(report.startsWith(TIPALLOG_16), "OSPITE_SINGOLO must start with code 16");
        assertTrue(report.substring(POS_COGNOME_START, POS_COGNOME_END).contains(SURNAME_ROSSI),
                "Cognome field at pos 14-63");
    }

    @Test
    void shouldNotHaveTrailingCrlfOnLastRecord() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertTrue(!report.endsWith(CRLF), "Last record must NOT be followed by CRLF");
    }

    @Test
    void shouldHaveCrlfBetweenRecordsButNotAfterLast() {
        final StayGuest capo = guestItalian(TravellerType.CAPOFAMIGLIA, true);
        final StayGuest familiare = guestFamiliare();
        stubArrivals(List.of(stayWith(capo, familiare)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        final String[] lines = report.split(CRLF, -1);
        assertEquals(2, lines.length, "Two records separated by one CRLF → 2 elements");
        assertEquals(EXPECTED_RECORD_LEN, lines[0].length(), "Record 0 must be 168 chars");
        assertEquals(EXPECTED_RECORD_LEN, lines[1].length(), "Record 1 must be 168 chars");
        assertTrue(!report.endsWith(CRLF), "No trailing CRLF");
    }

    @Test
    void shouldGenerateEmptyReportWhenNoCheckIns() {
        stubArrivals(List.of());

        final String report = service.generateReport(reportDate, hotelId);

        assertNotNull(report);
        assertTrue(report.isEmpty(), "No check-ins → empty report");
        verify(lookupService, never()).findComuneByCodice(any());
    }

    // -----------------------------------------------------------------------
    // 1000-row limit
    // -----------------------------------------------------------------------

    @Test
    void shouldThrowWhenRowLimitExceeded() {
        final int stayCount = AlloggiatiReportServiceImpl.MAX_ROWS_PER_FILE + 1;
        final List<Stay> stays = new ArrayList<>();
        for (int i = 0; i < stayCount; i++) {
            final StayGuest g = guestForeign(TravellerType.OSPITE_SINGOLO);
            stays.add(stayWith(g));
        }
        stubArrivals(stays);
        when(lookupService.findComuneByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        assertThrows(AlloggiatiRowLimitExceededException.class,
                () -> service.generateReport(reportDate, hotelId),
                "Should throw when row count exceeds " + AlloggiatiReportServiceImpl.MAX_ROWS_PER_FILE);
    }

    @Test
    void shouldNotThrowWhenRowCountEqualsLimit() {
        final List<Stay> stays = new ArrayList<>();
        for (int i = 0; i < AlloggiatiReportServiceImpl.MAX_ROWS_PER_FILE; i++) {
            stays.add(stayWith(guestForeign(TravellerType.OSPITE_SINGOLO)));
        }
        stubArrivals(stays);
        when(lookupService.findComuneByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.generateReport(reportDate, hotelId),
                "Exactly 1000 rows should not throw");
    }

    // -----------------------------------------------------------------------
    // Ordering: capo → components
    // -----------------------------------------------------------------------

    @Test
    void shouldOrderCapofamigliaBeforeFamiliare() {
        final StayGuest capo = guestItalian(TravellerType.CAPOFAMIGLIA, true);
        final StayGuest familiare = guestFamiliare();
        stubArrivals(List.of(stayWith(familiare, capo))); // reversed in input list
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        final String[] lines = report.split(CRLF, -1);
        assertEquals(2, lines.length, "Expected 2 records");
        assertTrue(lines[0].startsWith("17"), "First record must be CAPOFAMIGLIA (code 17)");
        assertTrue(lines[1].startsWith(TIPALLOG_19), "Second record must be FAMILIARE (code 19)");
    }

    @Test
    void shouldOrderCapogruppoBeforeMembroGruppo() {
        final StayGuest capo = guestItalian(TravellerType.CAPOGRUPPO, true);
        final StayGuest membro = guestMembroGruppo();
        stubArrivals(List.of(stayWith(membro, capo))); // reversed in input list
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        final String[] lines = report.split(CRLF, -1);
        assertEquals(2, lines.length, "Expected 2 records");
        assertTrue(lines[0].startsWith("18"), "First record must be CAPOGRUPPO (code 18)");
        assertTrue(lines[1].startsWith(TIPALLOG_20), "Second record must be MEMBRO_GRUPPO (code 20)");
    }

    // -----------------------------------------------------------------------
    // Document fields — FAMILIARE / MEMBRO_GRUPPO
    // -----------------------------------------------------------------------

    @Test
    void shouldBlankDocumentFieldsForFamiliare() {
        final StayGuest capo = guestItalian(TravellerType.CAPOFAMIGLIA, true);
        final StayGuest familiare = guestFamiliare();
        stubArrivals(List.of(stayWith(capo, familiare)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        final String[] lines = report.split(CRLF, -1);
        final String familiareLine = lines[1];
        assertEquals("     ", familiareLine.substring(POS_TIPO_DOC_START, POS_TIPO_DOC_END),
                "tipoDocumento must be 5 spaces for FAMILIARE");
        assertEquals("                    ", familiareLine.substring(POS_NUMERO_DOC_START, POS_NUMERO_DOC_END),
                "numeroDocumento must be 20 spaces for FAMILIARE");
        assertEquals(BLANK_9, familiareLine.substring(POS_LUOGO_RILASCIO_START, POS_LUOGO_RILASCIO_END),
                "luogoRilascioDoc must be 9 spaces for FAMILIARE");
    }

    @Test
    void shouldBlankDocumentFieldsForMembroGruppo() {
        final StayGuest capo = guestItalian(TravellerType.CAPOGRUPPO, true);
        final StayGuest membro = guestMembroGruppo();
        stubArrivals(List.of(stayWith(capo, membro)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        final String[] lines = report.split(CRLF, -1);
        final String membroLine = lines[1];
        assertEquals("     ", membroLine.substring(POS_TIPO_DOC_START, POS_TIPO_DOC_END),
                "tipoDocumento must be 5 spaces for MEMBRO_GRUPPO");
        assertEquals(BLANK_9, membroLine.substring(POS_LUOGO_RILASCIO_START, POS_LUOGO_RILASCIO_END),
                "luogoRilascioDoc must be 9 spaces for MEMBRO_GRUPPO");
    }

    // -----------------------------------------------------------------------
    // Birthplace: Italian vs foreign
    // -----------------------------------------------------------------------

    @Test
    void shouldUseBirthStatoCodeForForeignBornGuest() {
        final StayGuest guest = guestForeign(TravellerType.OSPITE_SINGOLO);
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_GERMANIA)).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertEquals(BLANK_9, report.substring(POS_COMUNE_NASCITA_START, POS_COMUNE_NASCITA_END),
                "comuneNascita must be blank for foreign-born");
        assertEquals(BLANK_2, report.substring(POS_PROVINCIA_NASCITA_START, POS_PROVINCIA_NASCITA_END),
                "provinciaNascita must be blank for foreign-born");
        assertEquals(CODICE_GERMANIA, report.substring(POS_STATO_NASCITA_START, POS_STATO_NASCITA_END),
                "statoNascita must be the foreign state code");
    }

    @Test
    void shouldSetStatoNascitaToItalyForItalianBorn() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertEquals(CODICE_ITALIA, report.substring(POS_STATO_NASCITA_START, POS_STATO_NASCITA_END));
        assertEquals(PROVINCIA_ROMA, report.substring(POS_PROVINCIA_NASCITA_START, POS_PROVINCIA_NASCITA_END));
    }

    // -----------------------------------------------------------------------
    // Permanenza
    // -----------------------------------------------------------------------

    @Test
    void shouldDefaultPermanenzaToOneWhenNullCheckOutDate() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        final Stay stay = Stay.builder()
                .id(UUID.randomUUID())
                .guestId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.of(YEAR, MONTH, DAY, CHECKIN_HOUR, 0))
                .expectedCheckOutDate(null)
                .build();
        stay.getGuests().add(guest);
        stubArrivals(List.of(stay));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertEquals("01", report.substring(POS_PERMANENZA_START, POS_PERMANENZA_END));
    }

    @Test
    void shouldClampPermanenzaAt30() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        final Stay stay = Stay.builder()
                .id(UUID.randomUUID())
                .guestId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.of(YEAR, MONTH, DAY, CHECKIN_HOUR, 0))
                .expectedCheckOutDate(reportDate.plusDays(NIGHTS_OVER_LIMIT))
                .build();
        stay.getGuests().add(guest);
        stubArrivals(List.of(stay));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertEquals("30", report.substring(POS_PERMANENZA_START, POS_PERMANENZA_END),
                "Permanenza must be clamped to 30");
    }

    // -----------------------------------------------------------------------
    // Domain validations
    // -----------------------------------------------------------------------

    @Test
    void shouldSkipStayWithNoGuests() {
        final Stay emptyStay = Stay.builder()
                .id(UUID.randomUUID())
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.of(YEAR, MONTH, DAY, CHECKIN_HOUR, 0))
                .build();
        stubArrivals(List.of(emptyStay));

        final String report = service.generateReport(reportDate, hotelId);

        assertTrue(report.isEmpty(), "Stay with no guests must be skipped");
    }

    // NOTE: these four validation-failure cases used to assert that a single invalid stay
    // aborted the WHOLE report (assertThrows). Fixed (2026-08-24 follow-up, REPORT.md §6 #3a):
    // one bad stay must not block every other guest checked in that day — the offending stay
    // is now skipped (logged at WARN) and the report/JSON export continues for the rest.
    // See shouldSkipOnlyTheInvalidStayAndKeepValidOnes below for the mixed-stay case that
    // actually proves the fix (a lone invalid stay skipped down to an empty report doesn't,
    // by itself, distinguish "skipped" from "still throwing but swallowed somewhere else").

    @Test
    void shouldSkipStayWhenFamiliareHasNoCapofamiglia() {
        final StayGuest familiare = guestFamiliare(); // no CAPOFAMIGLIA in stay
        stubArrivals(List.of(stayWith(familiare)));

        final String report = assertDoesNotThrow(() -> service.generateReport(reportDate, hotelId));
        assertTrue(report.isEmpty(), SKIPPED_NOT_THROWN_MSG);
    }

    @Test
    void shouldSkipStayWhenMembroGruppoHasNoCapogruppo() {
        final StayGuest membro = guestMembroGruppo();
        stubArrivals(List.of(stayWith(membro)));

        final String report = assertDoesNotThrow(() -> service.generateReport(reportDate, hotelId));
        assertTrue(report.isEmpty(), SKIPPED_NOT_THROWN_MSG);
    }

    @Test
    void shouldSkipStayWhenMultipleCapofamigliaInSameStay() {
        final StayGuest capo1 = guestItalian(TravellerType.CAPOFAMIGLIA, true);
        final StayGuest capo2 = guestItalian(TravellerType.CAPOFAMIGLIA, false);
        stubArrivals(List.of(stayWith(capo1, capo2)));

        final String report = assertDoesNotThrow(() -> service.generateReport(reportDate, hotelId));
        assertTrue(report.isEmpty(), SKIPPED_NOT_THROWN_MSG);
    }

    @Test
    void shouldSkipStayWhenCheckOutBeforeArrival() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        final Stay stay = Stay.builder()
                .id(UUID.randomUUID())
                .guestId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.of(YEAR, MONTH, DAY, CHECKIN_HOUR, 0))
                .expectedCheckOutDate(reportDate.minusDays(1)) // checkOut before arrival
                .build();
        stay.getGuests().add(guest);
        stubArrivals(List.of(stay));

        final String report = assertDoesNotThrow(() -> service.generateReport(reportDate, hotelId));
        assertTrue(report.isEmpty(), SKIPPED_NOT_THROWN_MSG);
    }

    @Test
    void shouldSkipOnlyTheInvalidStayAndKeepValidOnes() {
        final StayGuest validGuest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        final StayGuest invalidFamiliare = guestFamiliare(); // no CAPOFAMIGLIA in its own stay
        stubArrivals(List.of(stayWith(validGuest), stayWith(invalidFamiliare)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = assertDoesNotThrow(() -> service.generateReport(reportDate, hotelId),
                "one invalid stay must not abort the report for the other, valid stay");

        final String[] lines = report.split(CRLF, -1);
        assertEquals(1, lines.length, "only the valid stay's guest should produce a row");
        assertTrue(lines[0].startsWith(TIPALLOG_16), "the surviving record must be the valid OSPITE_SINGOLO");
    }

    // -----------------------------------------------------------------------
    // Edge cases: padding and truncation
    // -----------------------------------------------------------------------

    @Test
    void shouldTruncateCognomeExceedingMaxLength() {
        final String longSurname = "A".repeat(LEN_SURNAME_LONG); // >50 to trigger truncation
        final StayGuest guest = StayGuest.builder()
                .id(UUID.randomUUID())
                .firstName("Test")
                .lastName(longSurname)
                .gender(GENDER_MALE)
                .dateOfBirth(LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_ROMA)
                .citizenship(CODICE_ITALIA)
                .documentType(CODICE_TIPDOC_PASS)
                .documentNumber(DOC_NUMBER)
                .documentPlaceOfIssue(CODICE_ROMA)
                .isPrimaryGuest(true)
                .travellerType(TravellerType.OSPITE_SINGOLO)
                .build();
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertEquals(EXPECTED_RECORD_LEN, report.length(), "Record must still be 168 chars after truncation");
        assertEquals("A".repeat(LEN_COGNOME_MAX), report.substring(POS_COGNOME_START, POS_COGNOME_END),
                "Cognome truncated to 50 chars");
    }

    @Test
    void shouldHandleAccentedCharactersWithoutCorruption() {
        final StayGuest guest = StayGuest.builder()
                .id(UUID.randomUUID())
                .firstName("Søren")
                .lastName("Ångström")
                .gender(GENDER_MALE)
                .dateOfBirth(LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_GERMANIA)
                .citizenship(CODICE_GERMANIA)
                .documentType(CODICE_TIPDOC_PASS)
                .documentNumber("SE1234567")
                .documentPlaceOfIssue(CODICE_GERMANIA)
                .isPrimaryGuest(true)
                .travellerType(TravellerType.OSPITE_SINGOLO)
                .build();
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_GERMANIA)).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final List<AlloggiatiRowDto> rows = service.generateJsonReport(reportDate, hotelId);

        assertEquals(1, rows.size());
        assertEquals("Ångström", rows.get(0).cognome(),
                "Accented characters must be preserved in JSON export");
        assertEquals("Søren", rows.get(0).nome());
    }

    @Test
    void shouldHandleNullDateOfBirth() {
        final StayGuest guest = StayGuest.builder()
                .id(UUID.randomUUID())
                .firstName("Unknown")
                .lastName("Person")
                .gender(GENDER_MALE)
                .dateOfBirth(null) // explicitly null
                .placeOfBirth(CODICE_GERMANIA)
                .citizenship(CODICE_GERMANIA)
                .documentType(CODICE_TIPDOC_PASS)
                .documentNumber("X1234567")
                .documentPlaceOfIssue(CODICE_GERMANIA)
                .isPrimaryGuest(true)
                .travellerType(TravellerType.OSPITE_SINGOLO)
                .build();
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_GERMANIA)).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = assertDoesNotThrow(() -> service.generateReport(reportDate, hotelId),
                "Null dateOfBirth must not throw");

        assertEquals(EXPECTED_RECORD_LEN, report.length());
        // dataNascita field (pos 95-104) should be spaces
        assertEquals("          ", report.substring(POS_SESSO_END, POS_COMUNE_NASCITA_START),
                "Null dateOfBirth must produce 10 spaces in dataNascita field");
    }

    // -----------------------------------------------------------------------
    // Multiple stays in same export
    // -----------------------------------------------------------------------

    @Test
    void shouldGenerateMultipleStaysInSameExport() {
        final StayGuest italian = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        final StayGuest foreign = guestForeign(TravellerType.OSPITE_SINGOLO);
        stubArrivals(List.of(stayWith(italian), stayWith(foreign)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findComuneByCodice(CODICE_GERMANIA)).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        final String[] lines = report.split(CRLF, -1);
        assertEquals(2, lines.length, "Two stays with one guest each → 2 records");
        assertEquals(EXPECTED_RECORD_LEN, lines[0].length());
        assertEquals(EXPECTED_RECORD_LEN, lines[1].length());
    }

    // -----------------------------------------------------------------------
    // E2E compliance: realistic dataset scenarios (Task 7)
    // -----------------------------------------------------------------------

    @Test
    void e2eOspiteSingoloItaliano() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        // TIPALLOG=16, statoNascita=100000100, comuneNascita=058091000, provincia=RM
        assertEquals(TIPALLOG_16, report.substring(POS_TIPALLOG_START, POS_TIPALLOG_END));
        assertEquals(CODICE_ROMA, report.substring(POS_COMUNE_NASCITA_START, POS_COMUNE_NASCITA_END));
        assertEquals(PROVINCIA_ROMA, report.substring(POS_PROVINCIA_NASCITA_START, POS_PROVINCIA_NASCITA_END));
        assertEquals(CODICE_ITALIA, report.substring(POS_STATO_NASCITA_START, POS_STATO_NASCITA_END));
        assertEquals("1", report.substring(POS_SESSO_START, POS_SESSO_END));
        assertEquals(EXPECTED_RECORD_LEN, report.length());
    }

    @Test
    void e2eFamigliaCapofamigliaConFamiliari() {
        final StayGuest capo = guestItalian(TravellerType.CAPOFAMIGLIA, true);
        final StayGuest fam1 = guestFamiliare();
        final StayGuest fam2 = StayGuest.builder()
                .id(UUID.randomUUID()).firstName("Carlo").lastName(SURNAME_ROSSI).gender(GENDER_MALE)
                .dateOfBirth(LocalDate.of(YEAR_DOB + YEAR_DOB_OFFSET_5, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_ROMA).citizenship(CODICE_ITALIA)
                .documentType(null).documentNumber(null).documentPlaceOfIssue(null)
                .isPrimaryGuest(false).travellerType(TravellerType.FAMILIARE).build();
        stubArrivals(List.of(stayWith(fam1, capo, fam2))); // capo in middle
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);
        final String[] lines = report.split(CRLF, -1);

        assertEquals(3, lines.length, "3 guests → 3 records");
        assertEquals("17", lines[0].substring(POS_TIPALLOG_START, POS_TIPALLOG_END), "CAPOFAMIGLIA first");
        assertEquals(TIPALLOG_19, lines[1].substring(POS_TIPALLOG_START, POS_TIPALLOG_END), "FAMILIARE second");
        assertEquals(TIPALLOG_19, lines[2].substring(POS_TIPALLOG_START, POS_TIPALLOG_END), "FAMILIARE third");
        lines[0].chars().forEach(c -> assertTrue(c != '\r' && c != '\n', "No embedded CR/LF in record"));
    }

    @Test
    void e2eGruppoCapogruppoConMembri() {
        final StayGuest capo = guestItalian(TravellerType.CAPOGRUPPO, true);
        final StayGuest m1 = guestMembroGruppo();
        final StayGuest m2 = StayGuest.builder()
                .id(UUID.randomUUID()).firstName("Paolo").lastName("Verdi").gender(GENDER_MALE)
                .dateOfBirth(LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_ROMA).citizenship(CODICE_ITALIA)
                .documentType(null).documentNumber(null).documentPlaceOfIssue(null)
                .isPrimaryGuest(false).travellerType(TravellerType.MEMBRO_GRUPPO).build();
        stubArrivals(List.of(stayWith(m1, m2, capo)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);
        final String[] lines = report.split(CRLF, -1);

        assertEquals(3, lines.length, "3 guests → 3 records");
        assertEquals("18", lines[0].substring(POS_TIPALLOG_START, POS_TIPALLOG_END), "CAPOGRUPPO first");
        assertEquals(TIPALLOG_20, lines[1].substring(POS_TIPALLOG_START, POS_TIPALLOG_END), "MEMBRO_GRUPPO second");
        assertEquals(TIPALLOG_20, lines[2].substring(POS_TIPALLOG_START, POS_TIPALLOG_END), "MEMBRO_GRUPPO third");
    }

    @Test
    void e2eOspiteEsteroConDocumentoEstero() {
        final StayGuest guest = guestForeign(TravellerType.OSPITE_SINGOLO);
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_GERMANIA)).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);

        assertEquals(EXPECTED_RECORD_LEN, report.length());
        // comuneNascita and provinciaNascita blank, statoNascita = Germany
        assertEquals(BLANK_9, report.substring(POS_COMUNE_NASCITA_START, POS_COMUNE_NASCITA_END));
        assertEquals(BLANK_2, report.substring(POS_PROVINCIA_NASCITA_START, POS_PROVINCIA_NASCITA_END));
        assertEquals(CODICE_GERMANIA, report.substring(POS_STATO_NASCITA_START, POS_STATO_NASCITA_END));
        // luogoRilascio = stato code (Germany)
        assertEquals(CODICE_GERMANIA, report.substring(POS_LUOGO_RILASCIO_START, POS_LUOGO_RILASCIO_END));
        // doc fields NOT blank
        assertTrue(report.substring(POS_TIPO_DOC_START, POS_TIPO_DOC_END).contains("PASOR"),
                "tipoDocumento must be set for OSPITE_SINGOLO");
    }

    @Test
    void e2eCasoMistoItalianiEsteri() {
        final StayGuest italian = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        final StayGuest french = StayGuest.builder()
                .id(UUID.randomUUID()).firstName("Pierre").lastName("Dupont").gender(GENDER_MALE)
                .dateOfBirth(LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB))
                .placeOfBirth(CODICE_FRANCIA).citizenship(CODICE_FRANCIA)
                .documentType(CODICE_TIPDOC_PASS).documentNumber("FR1234567")
                .documentPlaceOfIssue(CODICE_FRANCIA)
                .isPrimaryGuest(true).travellerType(TravellerType.OSPITE_SINGOLO).build();
        stubArrivals(List.of(stayWith(italian), stayWith(french)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findComuneByCodice(CODICE_FRANCIA)).thenReturn(Optional.empty());
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final String report = service.generateReport(reportDate, hotelId);
        final String[] lines = report.split(CRLF, -1);

        assertEquals(2, lines.length);
        for (final String line : lines) {
            assertEquals(EXPECTED_RECORD_LEN, line.length(), "Each record must be 168 chars");
        }
        // Italian: statoNascita = Italy
        assertEquals(CODICE_ITALIA, lines[0].substring(POS_STATO_NASCITA_START, POS_STATO_NASCITA_END));
        // French: statoNascita = France
        assertEquals(CODICE_FRANCIA, lines[1].substring(POS_STATO_NASCITA_START, POS_STATO_NASCITA_END));
    }

    // -----------------------------------------------------------------------
    // generateJsonReport
    // -----------------------------------------------------------------------

    @Test
    void shouldGenerateJsonReportWithCorrectFields() {
        final StayGuest guest = guestItalian(TravellerType.OSPITE_SINGOLO, true);
        stubArrivals(List.of(stayWith(guest)));
        when(lookupService.findComuneByCodice(CODICE_ROMA)).thenReturn(Optional.of(comuneRoma()));
        when(lookupService.findStatoByCodice(anyString())).thenReturn(Optional.empty());
        when(lookupService.findTipdocByCodice(anyString())).thenReturn(Optional.empty());

        final List<AlloggiatiRowDto> rows = service.generateJsonReport(reportDate, hotelId);

        assertNotNull(rows);
        assertEquals(1, rows.size());
        final AlloggiatiRowDto r = rows.get(0);
        assertEquals(TIPALLOG_16, r.tipoAlloggiato());
        assertEquals(SURNAME_ROSSI, r.cognome());
        assertEquals("Mario", r.nome());
        assertEquals("1", r.sesso());
        assertEquals(CODICE_ROMA, r.comuneNascita());
        assertEquals(PROVINCIA_ROMA, r.provinciaNascita());
        assertEquals(CODICE_ITALIA, r.statoNascita());
        assertEquals(CODICE_ITALIA, r.cittadinanza());
        assertEquals(CODICE_TIPDOC_PASS, r.tipoDocumento());
        assertEquals(DOC_NUMBER, r.numeroDocumento());
        assertEquals(NIGHTS_TO_CHECKOUT, r.permanenza());
        verify(stayGuestRepository, times(1)).findByHotelIdAndArrivalDate(any(), any());
    }

    @Test
    void shouldGenerateEmptyJsonReportWhenNoCheckIns() {
        stubArrivals(List.of());

        final List<AlloggiatiRowDto> rows = service.generateJsonReport(reportDate, hotelId);

        assertNotNull(rows);
        assertTrue(rows.isEmpty());
        verify(lookupService, never()).findComuneByCodice(any());
    }
}
