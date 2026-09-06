package com.hotelpms.frontdesk.groups.dto;

import com.hotelpms.frontdesk.groups.domain.GroupStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a reservation group, including its rooming list.
 *
 * @param id                   the group's id
 * @param name                 display name
 * @param companyName          optional company/agency name
 * @param contactGuestId       the group's contact/responsible guest
 * @param contactGuestName     the contact guest's display name
 * @param checkInDate          check-in date shared by the rooming list
 * @param checkOutDate         check-out date shared by the rooming list
 * @param status               the group's lifecycle status
 * @param groupRatePerNight    the group rate, or {@code null} if rooms price normally
 * @param masterFolioInvoiceId the master folio's invoice id, or {@code null} if none
 * @param notes                free-text notes
 * @param members              the rooming list (one entry per room)
 * @param active               is active
 * @param createdAt            creation time
 * @param updatedAt            update time
 * @param version              the optimistic-lock version
 */
@SuppressWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public record ReservationGroupResponse(
        UUID id,
        String name,
        String companyName,
        UUID contactGuestId,
        String contactGuestName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        GroupStatus status,
        BigDecimal groupRatePerNight,
        UUID masterFolioInvoiceId,
        String notes,
        List<GroupMemberResponse> members,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) {

    /**
     * Compact constructor: defensive copy of the rooming list.
     */
    public ReservationGroupResponse {
        if (members != null) {
            members = List.copyOf(members);
        }
    }

    /**
     * Getter for the rooming list.
     *
     * @return the rooming list
     */
    @Override
    public List<GroupMemberResponse> members() {
        return members == null ? null : List.copyOf(members);
    }
}
