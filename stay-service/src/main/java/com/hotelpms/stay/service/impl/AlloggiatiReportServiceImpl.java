package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.client.GuestClient;
import com.hotelpms.stay.client.dto.AlloggiatiGuestResponse;
import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.dto.AlloggiatiRowDto;
import com.hotelpms.stay.repository.StayRepository;
import com.hotelpms.stay.service.AlloggiatiReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the Italian Alloggiati Web police report for daily check-ins.
 * Output is a newline-separated, pipe-delimited text where each line contains:
 * Cognome|Nome|DataNascita|TipoDocumento|NumeroDocumento|DataArrivo
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlloggiatiReportServiceImpl implements AlloggiatiReportService {

    private static final DateTimeFormatter ITALIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String EMPTY_DOB = "N/A";
    private static final String NO_DOCUMENT = "N/A";
    private static final String FIELD_SEPARATOR = "|";
    private static final String LINE_SEPARATOR = "\r\n";

    private final StayRepository stayRepository;
    private final GuestClient guestClient;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public String generateReport(final LocalDate date) {
        log.info("Generating Alloggiati Web report for date: {}", date);

        final LocalDateTime start = date.atStartOfDay();
        final LocalDateTime end = date.plusDays(1).atStartOfDay();

        final List<Stay> checkIns = stayRepository.findByActualCheckInTimeBetween(start, end);
        log.info("Found {} check-ins for {}", checkIns.size(), date);

        final StringBuilder report = new StringBuilder();
        for (final Stay stay : checkIns) {
            final AlloggiatiRowDto row = buildRow(stay, date);
            appendRow(report, row);
        }
        return report.toString();
    }

    private AlloggiatiRowDto buildRow(final Stay stay, final LocalDate arrivalDate) {
        final AlloggiatiGuestResponse guest = guestClient.getGuestDetailsById(stay.getGuestId());

        final String dob = guest.dateOfBirth() != null
                ? guest.dateOfBirth().format(ITALIAN_DATE)
                : EMPTY_DOB;

        String docType = NO_DOCUMENT;
        String docNumber = NO_DOCUMENT;

        if (guest.identityDocuments() != null && !guest.identityDocuments().isEmpty()) {
            final AlloggiatiGuestResponse.AlloggiatiDocumentResponse doc = guest.identityDocuments().get(0);
            docType = doc.documentType();
            docNumber = doc.documentNumber();
        }

        return new AlloggiatiRowDto(
                guest.lastName(),
                guest.firstName(),
                dob,
                docType,
                docNumber,
                arrivalDate.format(ITALIAN_DATE));
    }

    private void appendRow(final StringBuilder report, final AlloggiatiRowDto row) {
        report.append(row.lastName())
                .append(FIELD_SEPARATOR)
                .append(row.firstName())
                .append(FIELD_SEPARATOR)
                .append(row.dateOfBirth())
                .append(FIELD_SEPARATOR)
                .append(row.documentType())
                .append(FIELD_SEPARATOR)
                .append(row.documentNumber())
                .append(FIELD_SEPARATOR)
                .append(row.arrivalDate())
                .append(LINE_SEPARATOR);
    }
}
