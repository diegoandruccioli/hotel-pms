package com.hotelpms.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;

/**
 * Entity class representing a Stay in the hotel.
 * A Stay links a Guest, a Reservation, and a Room.
 */
@Entity
@Table(name = "stays")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE stays SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class Stay {

    /**
     * Maximum length for the status column.
     */
    public static final int MAX_STATUS_LENGTH = 20;

    /**
     * Unique identifier for the Stay.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The hotel this stay belongs to (multi-tenancy support).
     */
    @Column(name = "hotel_id")
    private UUID hotelId;

    /**
     * The ID of the reservation associated with this stay.
     */
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    /**
     * The ID of the guest associated with this stay.
     */
    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    /**
     * The ID of the room associated with this stay.
     */
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    /**
     * The current status of the stay.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = MAX_STATUS_LENGTH)
    private StayStatus status;

    /**
     * The actual time the guest checked in.
     */
    @Column(name = "actual_check_in_time")
    private LocalDateTime actualCheckInTime;

    /**
     * The actual time the guest checked out.
     */
    @Column(name = "actual_check_out_time")
    private LocalDateTime actualCheckOutTime;

    /**
     * The timestamp when the record was created.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * The timestamp when the record was last updated.
     */
    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    /**
     * Flag indicating whether the record is active (for soft deletion).
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * The list of guests staying in this room.
     */
    @OneToMany(mappedBy = "stay", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StayGuest> guests = new ArrayList<>();
}
