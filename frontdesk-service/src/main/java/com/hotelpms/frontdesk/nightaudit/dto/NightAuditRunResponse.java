package com.hotelpms.frontdesk.nightaudit.dto;

import com.hotelpms.frontdesk.client.dto.PaymentMethodTotalDto;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A single night-audit closing record.
 *
 * @param id                  the run UUID
 * @param businessDate        the business date this run covers
 * @param status              RUNNING (never actually observed — the run completes
 *                            synchronously within the triggering call), COMPLETED, or FAILED
 * @param startedAt           when the run started
 * @param completedAt         when the run finished, {@code null} while RUNNING
 * @param runBy               who/what triggered the run ({@code "system"} for the scheduled path)
 * @param arrivals            arrivals snapshot for the business date
 * @param departures          departures snapshot for the business date
 * @param guestsInHouse       guests in house at run time
 * @param currentStays        checked-in stays at run time
 * @param availableRooms      rooms available for the business date
 * @param noShowsMarked       reservations auto-transitioned to NO_SHOW by this run
 * @param cashByMethod        payments by method for the business date
 * @param cashSummaryDegraded {@code true} when billing-service was unreachable at run time
 * @param failureReason       set only when {@code status=FAILED}
 */
public record NightAuditRunResponse(
        UUID id,
        LocalDate businessDate,
        NightAuditStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String runBy,
        Integer arrivals,
        Integer departures,
        Long guestsInHouse,
        Long currentStays,
        Integer availableRooms,
        Integer noShowsMarked,
        List<PaymentMethodTotalDto> cashByMethod,
        boolean cashSummaryDegraded,
        String failureReason) {

    /**
     * Defensively copies {@code cashByMethod}.
     */
    public NightAuditRunResponse {
        cashByMethod = cashByMethod == null ? List.of() : List.copyOf(cashByMethod);
    }
}
