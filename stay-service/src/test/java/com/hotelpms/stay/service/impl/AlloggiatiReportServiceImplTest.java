package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.client.GuestClient;
import com.hotelpms.stay.client.dto.AlloggiatiGuestResponse;
import com.hotelpms.stay.domain.Stay;
import com.hotelpms.stay.domain.StayStatus;
import com.hotelpms.stay.repository.StayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlloggiatiReportServiceImplTest {

        private static final int YEAR = 2026;
        private static final int MONTH_MAR = 3;
        private static final int DAY_MAR_2 = 2;
        private static final int HOUR_14 = 14;
        private static final int HOUR_16 = 16;
        private static final int MIN_30 = 30;
        private static final int YEAR_DOB = 1985;
        private static final int MONTH_DOB = 5;
        private static final int DAY_DOB = 20;

        @Mock
        private StayRepository stayRepository;

        @Mock
        private GuestClient guestClient;

        @InjectMocks
        private AlloggiatiReportServiceImpl alloggiatiReportService;

        private LocalDate reportDate;
        private Stay stayOne;
        private Stay stayTwo;
        private AlloggiatiGuestResponse guestOne;
        private AlloggiatiGuestResponse guestTwo;

        @BeforeEach
        void setUp() {
                reportDate = LocalDate.of(YEAR, MONTH_MAR, DAY_MAR_2);

                final UUID guestOneId = UUID.randomUUID();
                final UUID guestTwoId = UUID.randomUUID();

                stayOne = Stay.builder()
                                .id(UUID.randomUUID())
                                .guestId(guestOneId)
                                .roomId(UUID.randomUUID())
                                .status(StayStatus.CHECKED_IN)
                                .actualCheckInTime(LocalDateTime.of(YEAR, MONTH_MAR, DAY_MAR_2, HOUR_14, 0))
                                .build();

                stayTwo = Stay.builder()
                                .id(UUID.randomUUID())
                                .guestId(guestTwoId)
                                .roomId(UUID.randomUUID())
                                .status(StayStatus.CHECKED_IN)
                                .actualCheckInTime(LocalDateTime.of(YEAR, MONTH_MAR, DAY_MAR_2, HOUR_16, MIN_30))
                                .build();

                guestOne = new AlloggiatiGuestResponse(
                                guestOneId, "Rossi", "Mario", LocalDate.of(YEAR_DOB, MONTH_DOB, DAY_DOB),
                                List.of(new AlloggiatiGuestResponse.AlloggiatiDocumentResponse("PASSPORT",
                                                "AA123456")));

                guestTwo = new AlloggiatiGuestResponse(
                                guestTwoId, "Bianchi", "Lucia", null,
                                List.of());
        }

        @Test
        void shouldGenerateReportWithTwoRows() {
                // Arrange
                when(stayRepository.findByActualCheckInTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(stayOne, stayTwo));
                when(guestClient.getGuestDetailsById(stayOne.getGuestId())).thenReturn(guestOne);
                when(guestClient.getGuestDetailsById(stayTwo.getGuestId())).thenReturn(guestTwo);

                // Act
                final String report = alloggiatiReportService.generateReport(reportDate);

                // Assert
                assertNotNull(report);
                assertTrue(report.contains("Rossi"), "Report should contain guest one's last name");
                assertTrue(report.contains("Mario"), "Report should contain guest one's first name");
                assertTrue(report.contains("20/05/1985"), "Report should contain guest one's formatted DOB");
                assertTrue(report.contains("PASSPORT"), "Report should contain document type");
                assertTrue(report.contains("AA123456"), "Report should contain document number");
                assertTrue(report.contains("02/03/2026"), "Report should contain arrival date");
                assertTrue(report.contains("Bianchi"), "Report should contain guest two's last name");
                assertTrue(report.contains("N/A"), "Report should contain N/A for missing DOB");

                verify(stayRepository, times(1)).findByActualCheckInTimeBetween(any(), any());
                verify(guestClient, times(1)).getGuestDetailsById(stayOne.getGuestId());
                verify(guestClient, times(1)).getGuestDetailsById(stayTwo.getGuestId());
        }

        @Test
        void shouldReturnEmptyReportWhenNoCheckIns() {
                // Arrange
                when(stayRepository.findByActualCheckInTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of());

                // Act
                final String report = alloggiatiReportService.generateReport(reportDate);

                // Assert
                assertNotNull(report);
                assertTrue(report.isEmpty(), "Report should be empty when no check-ins found");
                verify(guestClient, times(0)).getGuestDetailsById(any());
        }
}
