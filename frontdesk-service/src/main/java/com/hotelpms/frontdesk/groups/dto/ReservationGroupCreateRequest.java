package com.hotelpms.frontdesk.groups.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request to create a reservation group together with its rooming list: one child
 * {@code Reservation} is created per {@link RoomingListEntryRequest} row, in the
 * same transaction as the group itself.
 *
 * @param name               a display name for the group (company/party/event)
 * @param companyName        optional company/agency name, separate from the display name
 * @param contactGuestId     the group's contact/responsible guest -- also the
 *                           guestId used for the master folio invoice, if opened
 * @param checkInDate        check-in date shared by every room in the rooming list
 * @param checkOutDate       check-out date shared by every room in the rooming list
 * @param groupRatePerNight  when set, takes precedence over the room-type rate
 *                           calendar for every reservation created here
 * @param notes              free-text notes
 * @param openMasterFolio    when {@code true}, a MASTER invoice is opened in
 *                           billing-service for this group
 * @param rooms              the rooming list; one child reservation per entry
 */
public record ReservationGroupCreateRequest(
        @NotBlank(message = REQUIRED) String name,
        String companyName,
        @NotNull(message = REQUIRED) UUID contactGuestId,
        @NotNull(message = REQUIRED) @FutureOrPresent(message = "Future") LocalDate checkInDate,
        @NotNull(message = REQUIRED) @FutureOrPresent(message = "Future") LocalDate checkOutDate,
        @PositiveOrZero(message = "Must not be negative") BigDecimal groupRatePerNight,
        String notes,
        boolean openMasterFolio,
        @NotEmpty(message = REQUIRED)
        @Size(max = MAX_ROOMS, message = "Too many rooms") @Valid List<RoomingListEntryRequest> rooms) {

    static final String REQUIRED = "Required";

    /**
     * Upper bound on rooms in a single group, same order of magnitude as {@code
     * ReservationRequest.MAX_LINE_ITEMS} (a group is bigger than one reservation,
     * but still a single hotel-sized event, not an unbounded batch import).
     */
    static final int MAX_ROOMS = 100;

    /**
     * Compact constructor: defensive copy of the rooming list.
     */
    public ReservationGroupCreateRequest {
        if (rooms != null) {
            rooms = List.copyOf(rooms);
        }
    }

    /**
     * Getter for the rooming list.
     *
     * @return the rooming list
     */
    @Override
    public List<RoomingListEntryRequest> rooms() {
        return rooms == null ? null : List.copyOf(rooms);
    }
}
