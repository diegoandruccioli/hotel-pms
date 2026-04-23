package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.ReservationClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.ReservationResponse;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceChargeMapper;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final InvoiceChargeMapper invoiceChargeMapper;
    private final GuestClient guestClient;
    private final ReservationClient reservationClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse createInvoice(@NonNull final InvoiceRequest request) {
        log.info("Creating invoice for reservation {} and guest {}", request.reservationId(), request.guestId());

        final GuestResponse guest = guestClient.getGuestById(request.guestId());
        if (guest == null || guest.id() == null) {
            throw new BillingValidationException("INVALID_GUEST_DETAILS");
        }

        final ReservationResponse reservation = reservationClient.getReservationById(request.reservationId());
        if (reservation == null || reservation.id() == null) {
            throw new BillingValidationException("INVALID_RESERVATION_DETAILS");
        }

        if (!reservation.guestId().equals(request.guestId())) {
            throw new BillingValidationException("GUEST_MISMATCH");
        }

        if (request.totalAmount().compareTo(reservation.totalPrice()) != 0) {
            log.warn("Invoice amount {} differs from reservation amount {}", request.totalAmount(),
                    reservation.totalPrice());
        }

        final Invoice invoice = invoiceMapper.toEntity(request);
        invoice.setIssueDate(LocalDateTime.now());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setHotelId(resolveHotelId());

        final Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Successfully created Invoice {}", savedInvoice.getInvoiceNumber());

        return invoiceMapper.toResponse(savedInvoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InvoiceResponse createInvoiceForStay(@NonNull final StayInvoiceRequest request) {
        log.info("Creating invoice for stay {}", request.stayId());
        final UUID hotelId = resolveHotelId();

        invoiceRepository.findByStayIdAndHotelId(request.stayId(), hotelId)
                .filter(existing -> existing.getStatus() == InvoiceStatus.ISSUED)
                .ifPresent(existing -> {
                    throw new InvoiceConflictException("INVOICE_ALREADY_EXISTS_FOR_STAY");
                });

        final Invoice invoice = Invoice.builder()
                .stayId(request.stayId())
                .guestId(request.guestId())
                .reservationId(request.reservationId())
                .hotelId(hotelId)
                .totalAmount(BigDecimal.ZERO)
                .status(InvoiceStatus.ISSUED)
                .issueDate(LocalDateTime.now())
                .invoiceNumber(generateInvoiceNumber())
                .build();

        final Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Created invoice {} for stay {}", savedInvoice.getInvoiceNumber(), request.stayId());

        return invoiceMapper.toResponse(savedInvoice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ChargeResponse addCharge(@NonNull final UUID stayId, @NonNull final ChargeRequest request) {
        log.info("Adding charge type={} amount={} to stay {}", request.type(), request.amount(), stayId);
        final UUID hotelId = resolveHotelId();

        final Invoice invoice = invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND_FOR_STAY"));

        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new InvoiceConflictException("INVOICE_NOT_OPEN");
        }

        final InvoiceCharge charge = InvoiceCharge.builder()
                .type(request.type())
                .description(request.description())
                .amount(request.amount())
                .referenceId(request.referenceId())
                .build();

        invoice.addCharge(charge);
        invoice.setTotalAmount(invoice.getTotalAmount().add(request.amount()));
        invoiceRepository.save(invoice);

        log.info("Added {} charge of {} to invoice {} (new total: {})",
                request.type(), request.amount(), invoice.getInvoiceNumber(), invoice.getTotalAmount());

        return invoiceChargeMapper.toResponse(charge);
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
     * The hotel ID is stored as {@code details} by
     * {@link com.hotelpms.billing.security.InternalAuthFilter} after reading the
     * {@code X-Auth-Hotel} header injected by the API Gateway.
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

    /**
     * Generates a unique, human-readable invoice number.
     *
     * @return invoice number in the format {@code INV-XXXXXXXX}
     */
    private String generateInvoiceNumber() {
        return "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
