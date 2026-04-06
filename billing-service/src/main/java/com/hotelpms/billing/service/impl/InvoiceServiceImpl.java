package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.ReservationClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.ReservationResponse;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of the InvoiceService interface for billing processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final GuestClient guestClient;
    private final ReservationClient reservationClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse createInvoice(@NonNull final InvoiceRequest request) {
        log.info("Creating invoice for reservation {} and guest {}", request.reservationId(), request.guestId());

        // 1. Validate Guest existence
        final GuestResponse guest = guestClient.getGuestById(request.guestId());
        if (guest == null || guest.id() == null) {
            throw new BillingValidationException("INVALID_GUEST_DETAILS");
        }

        // 2. Validate Reservation existence
        final ReservationResponse reservation = reservationClient.getReservationById(request.reservationId());
        if (reservation == null || reservation.id() == null) {
            throw new BillingValidationException("INVALID_RESERVATION_DETAILS");
        }

        // 3. Ensure integrity
        if (!reservation.guestId().equals(request.guestId())) {
            throw new BillingValidationException("GUEST_MISMATCH");
        }

        if (request.totalAmount().compareTo(reservation.totalPrice()) != 0) {
            log.warn("Invoice amount {} differs from reservation amount {}", request.totalAmount(),
                    reservation.totalPrice());
        }

        final Invoice invoice = invoiceMapper.toEntity(request);
        invoice.setIssueDate(LocalDateTime.now());
        invoice.setInvoiceNumber(
                "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
        // Bind invoice to the caller's hotel — never trust the client-supplied value.
        invoice.setHotelId(resolveHotelId());

        final Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Successfully created Invoice {}", savedInvoice.getInvoiceNumber());

        return invoiceMapper.toResponse(savedInvoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(@NonNull final UUID id) {
        log.info("Fetching invoice with id {}", id);
        final UUID hotelId = resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND"));
        return invoiceMapper.toResponse(invoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getLatestInvoiceByReservation(@NonNull final UUID reservationId) {
        log.info("Fetching latest invoice for reservation {}", reservationId);
        final UUID hotelId = resolveHotelId();
        final Invoice invoice = invoiceRepository
                .findFirstByReservationIdAndHotelIdOrderByIssueDateDesc(reservationId, hotelId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND"));
        return invoiceMapper.toResponse(invoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getAllInvoices(final Pageable pageable) {
        final Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        final UUID hotelId = resolveHotelId();
        return invoiceRepository.findByHotelId(hotelId, safePageable)
                .map(invoiceMapper::toResponse);
    }

    /**
     * Extracts the hotel UUID from the current authentication context.
     * The hotel ID is stored as {@code details} by {@link com.hotelpms.billing.security.InternalAuthFilter}
     * after reading the {@code X-Auth-Hotel} header injected by the API Gateway.
     *
     * @return the hotel UUID of the authenticated caller
     * @throws IllegalStateException if the security context is missing or malformed
     */
    private UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr)) {
            throw new IllegalStateException("MISSING_HOTEL_CONTEXT");
        }
        return UUID.fromString(hotelIdStr);
    }
}
