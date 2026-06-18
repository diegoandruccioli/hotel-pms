package com.hotelpms.frontdesk.reservations.repository;

import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for ReservationLineItem.
 */
@Repository
public interface ReservationLineItemRepository extends JpaRepository<ReservationLineItem, UUID> {
}
