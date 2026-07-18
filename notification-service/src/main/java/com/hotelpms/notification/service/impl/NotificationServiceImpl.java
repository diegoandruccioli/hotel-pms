package com.hotelpms.notification.service.impl;

import com.hotelpms.notification.dto.CheckinNotificationRequest;
import com.hotelpms.notification.dto.CheckoutNotificationRequest;
import com.hotelpms.notification.dto.ReservationConfirmedRequest;
import com.hotelpms.notification.service.NotificationService;
import com.hotelpms.notification.util.EmailMasker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

/**
 * Renders Thymeleaf HTML templates and dispatches email via JavaMailSender.
 */
@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private static final String DEFAULT_LOCALE = "it";
    private static final String ENGLISH_LOCALE = "en";
    private static final String CHARSET = "UTF-8";
    private static final String TEMPLATE_VAR_REQUEST = "req";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;
    private final String fromName;

    /**
     * Constructs the service with injected mail infrastructure and configuration.
     *
     * @param mailSender       the Spring JavaMailSender configured with SMTP settings
     * @param templateEngine   the Thymeleaf engine used to render HTML templates
     * @param fromAddress      the "From" email address ({@code notification.from-address})
     * @param fromName         the "From" display name ({@code notification.from-name})
     */
    public NotificationServiceImpl(
            final JavaMailSender mailSender,
            final TemplateEngine templateEngine,
            @Value("${notification.from-address}") final String fromAddress,
            @Value("${notification.from-name:Hotel PMS}") final String fromName) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    /** {@inheritDoc} */
    @Override
    public void sendReservationConfirmed(final ReservationConfirmedRequest request) {
        final String template = "email/reservation-confirmed-" + sanitizeLocale(request.locale());
        final Context ctx = new Context();
        ctx.setVariable(TEMPLATE_VAR_REQUEST, request);
        final String html = templateEngine.process(template, ctx);
        final String subject = buildSubject("Conferma prenotazione", "Booking confirmation", request.locale(),
                request.hotelName(), request.customSubject());
        sendHtmlEmail(request.guestEmail(), subject, html, request.hotelName());
        log.info("[NOTIFY] reservation-confirmed sent | to={}", EmailMasker.mask(request.guestEmail()));
    }

    /** {@inheritDoc} */
    @Override
    public void sendCheckin(final CheckinNotificationRequest request) {
        final String template = "email/checkin-" + sanitizeLocale(request.locale());
        final Context ctx = new Context();
        ctx.setVariable(TEMPLATE_VAR_REQUEST, request);
        final String html = templateEngine.process(template, ctx);
        final String subject = buildSubject("Benvenuto al check-in", "Welcome — Check-in confirmed",
                request.locale(), request.hotelName(), null);
        sendHtmlEmail(request.guestEmail(), subject, html, request.hotelName());
        log.info("[NOTIFY] checkin sent | to={}", EmailMasker.mask(request.guestEmail()));
    }

    /** {@inheritDoc} */
    @Override
    public void sendCheckout(final CheckoutNotificationRequest request) {
        final String template = "email/checkout-" + sanitizeLocale(request.locale());
        final Context ctx = new Context();
        ctx.setVariable(TEMPLATE_VAR_REQUEST, request);
        final String html = templateEngine.process(template, ctx);
        final String subject = buildSubject("Riepilogo soggiorno e fattura", "Stay summary and invoice",
                request.locale(), request.hotelName(), request.customSubject());
        sendHtmlEmail(request.guestEmail(), subject, html, request.hotelName());
        log.info("[NOTIFY] checkout sent | to={}", EmailMasker.mask(request.guestEmail()));
    }

    /**
     * Creates and sends a MIME HTML email message.
     *
     * @param to        the recipient address
     * @param subject   the email subject
     * @param html      the rendered HTML body
     * @param hotelName the hotel display name for the "From" header (ADR-005: the
     *                  technical address stays platform-wide — no per-hotel domain
     *                  exists yet — but the guest-visible sender name is the hotel's)
     */
    private void sendHtmlEmail(final String to, final String subject, final String html, final String hotelName) {
        try {
            final MimeMessage message = mailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, true, CHARSET);
            helper.setFrom(new InternetAddress(fromAddress, resolveFromName(hotelName), CHARSET));
            helper.setTo(java.util.Objects.requireNonNull(to));
            helper.setSubject(java.util.Objects.requireNonNull(subject));
            helper.setText(java.util.Objects.requireNonNull(html), true);
            mailSender.send(message);
        } catch (final MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("EMAIL_SEND_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the "From" display name: the hotel's own name when available,
     * falling back to the platform default otherwise.
     *
     * @param hotelName the hotel display name (may be null or blank)
     * @return the name to use for the "From" header
     */
    private String resolveFromName(final String hotelName) {
        return (hotelName != null && !hotelName.isBlank()) ? hotelName : fromName;
    }

    /**
     * Returns "it" or "en" based on the locale string; defaults to "it" for unknown locales.
     *
     * @param locale the requested locale tag
     * @return the sanitized template locale suffix
     */
    private static String sanitizeLocale(final String locale) {
        return ENGLISH_LOCALE.equals(locale) ? ENGLISH_LOCALE : DEFAULT_LOCALE;
    }

    /**
     * Builds the email subject line, preferring a per-hotel custom subject when set,
     * otherwise falling back to the default IT/EN subject with an optional hotel name suffix.
     *
     * @param italian       Italian default subject
     * @param english       English default subject
     * @param locale        requested locale
     * @param hotelName     hotel name to append to the default subject (may be null)
     * @param customSubject per-hotel subject override; used verbatim when non-blank
     * @return the full subject string
     */
    private static String buildSubject(final String italian, final String english,
            final String locale, final String hotelName, final String customSubject) {
        if (customSubject != null && !customSubject.isBlank()) {
            return customSubject.trim();
        }
        final String base = ENGLISH_LOCALE.equals(locale) ? english : italian;
        return (hotelName != null && !hotelName.isBlank()) ? base + " — " + hotelName : base;
    }
}
