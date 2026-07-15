package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsRequest;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link HotelSettingsService}.
 */
@Service
@RequiredArgsConstructor
public class HotelSettingsServiceImpl implements HotelSettingsService {

    private final HotelSettingsRepository hotelSettingsRepository;
    private final AlloggiatiCredentialEncryptor alloggiatiCredentialEncryptor;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public HotelSettingsResponse getOrCreate(final UUID hotelId) {
        return hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))
                .map(HotelSettingsServiceImpl::toResponse)
                .orElseGet(() -> toResponse(createDefault(hotelId)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public HotelSettingsResponse update(final UUID hotelId, final HotelSettingsRequest request) {
        final HotelSettings settings = hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))
                .orElseGet(() -> buildDefault(hotelId));
        // Partial-patch semantics: a null field means "absent from the request", not
        // "clear this value" — callers such as a single settings toggle must be able to
        // send only the field they changed without wiping out the rest of the profile.
        if (request.alloggiatiAutoSend() != null) {
            settings.setAlloggiatiAutoSend(request.alloggiatiAutoSend());
        }
        if (request.hotelName() != null) {
            settings.setHotelName(request.hotelName());
        }
        if (request.address() != null) {
            settings.setAddress(request.address());
        }
        if (request.vatNumber() != null) {
            settings.setVatNumber(request.vatNumber());
        }
        if (request.fiscalCode() != null) {
            settings.setFiscalCode(request.fiscalCode());
        }
        if (request.logoUrl() != null) {
            settings.setLogoUrl(request.logoUrl());
        }
        if (request.alloggiatiUsername() != null) {
            settings.setAlloggiatiUsername(request.alloggiatiUsername());
        }
        if (request.alloggiatiPassword() != null && !request.alloggiatiPassword().isBlank()) {
            settings.setAlloggiatiPasswordEncrypted(alloggiatiCredentialEncryptor.encrypt(request.alloggiatiPassword()));
        }
        if (request.alloggiatiWsKey() != null && !request.alloggiatiWsKey().isBlank()) {
            settings.setAlloggiatiWsKeyEncrypted(alloggiatiCredentialEncryptor.encrypt(request.alloggiatiWsKey()));
        }
        if (request.sendReservationConfirmedEmail() != null) {
            settings.setSendReservationConfirmedEmail(request.sendReservationConfirmedEmail());
        }
        if (request.sendCheckoutEmail() != null) {
            settings.setSendCheckoutEmail(request.sendCheckoutEmail());
        }
        if (request.emailSubjectReservationConfirmed() != null) {
            settings.setEmailSubjectReservationConfirmed(request.emailSubjectReservationConfirmed());
        }
        if (request.emailSubjectCheckout() != null) {
            settings.setEmailSubjectCheckout(request.emailSubjectCheckout());
        }
        if (request.emailGreetingText() != null) {
            settings.setEmailGreetingText(request.emailGreetingText());
        }
        return toResponse(hotelSettingsRepository.save(settings));
    }

    private HotelSettings createDefault(final UUID hotelId) {
        return hotelSettingsRepository.save(Objects.requireNonNull(buildDefault(hotelId)));
    }

    private static HotelSettings buildDefault(final UUID hotelId) {
        final HotelSettings defaults = new HotelSettings();
        defaults.setHotelId(hotelId);
        return defaults;
    }

    private static HotelSettingsResponse toResponse(final HotelSettings entity) {
        return new HotelSettingsResponse(
                entity.getHotelId(),
                entity.isAlloggiatiAutoSend(),
                entity.getHotelName(),
                entity.getAddress(),
                entity.getVatNumber(),
                entity.getFiscalCode(),
                entity.getLogoUrl(),
                entity.getAlloggiatiUsername(),
                entity.hasAlloggiatiCredentials(),
                entity.isSendReservationConfirmedEmail(),
                entity.isSendCheckoutEmail(),
                entity.getEmailSubjectReservationConfirmed(),
                entity.getEmailSubjectCheckout(),
                entity.getEmailGreetingText());
    }
}
