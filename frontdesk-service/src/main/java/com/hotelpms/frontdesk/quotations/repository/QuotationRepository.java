package com.hotelpms.frontdesk.quotations.repository;

import com.hotelpms.frontdesk.quotations.domain.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Quotation.
 */
public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    /**
     * Lists every active quotation for a hotel, newest first.
     *
     * @param hotelId  the hotel UUID (multi-tenant scoping)
     * @param pageable pagination and sorting
     * @return a page of quotations
     */
    Page<Quotation> findAllByHotelId(@Param("hotelId") @NonNull UUID hotelId, @NonNull Pageable pageable);

    /**
     * Finds a quotation by id, scoped to a hotel.
     *
     * @param id      the quotation UUID
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the quotation, if it exists and belongs to this hotel
     */
    Optional<Quotation> findByIdAndHotelId(@Param("id") @NonNull UUID id, @Param("hotelId") @NonNull UUID hotelId);
}
