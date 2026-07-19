package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.HotelSettingsClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.PdfInvoiceService;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Generates A4 PDF invoices/receipts by rendering a Thymeleaf HTML template with
 * {@link PdfTemplateRenderer} (see {@code pdf-template-engine} module). One template
 * per {@link DocumentType} — FATTURA needs a VAT breakdown section, RICEVUTA does not —
 * selected by {@link #templateFor(DocumentType)}.
 *
 * <p>Hotel header data is fetched from stay-service; guest name from guest-service.
 * Both Feign calls use circuit-breaker fallbacks so PDF generation never blocks.
 *
 * <p>This class owns all domain formatting (amounts, dates, VAT grouping, charge/payment
 * type labels) — the templates are display-only, no business logic in markup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfInvoiceServiceImpl implements PdfInvoiceService {

    private static final String TEMPLATE_FATTURA = "invoice-fattura";
    private static final String TEMPLATE_RICEVUTA = "invoice-ricevuta";

    // Classpath location for the dev-provided hotel logo (never admin-uploaded —
    // see ADR in backup/DECISIONS.md: uploads are an attack surface, a static asset
    // shipped by the developer is not). Optional: absent means no logo in the PDF,
    // the template guards on this with th:if.
    private static final String LOGO_CLASSPATH_RESOURCE = "static/pdf/logo.png";
    private static final String LOGO_DATA_URI_PREFIX = "data:image/png;base64,";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String EMPTY_VALUE = "---";

    private final InvoiceService invoiceService;
    private final HotelSettingsClient hotelSettingsClient;
    private final GuestClient guestClient;
    private final PdfTemplateRenderer pdfTemplateRenderer;

    /** {@inheritDoc} */
    @Override
    public byte[] generateInvoicePdf(@NonNull final UUID invoiceId) {
        log.info("Generating PDF for invoice {}", invoiceId);
        final InvoiceResponse invoice = invoiceService.getInvoice(invoiceId);
        final HotelSettingsResponse hotel = hotelSettingsClient.getSettings();
        final GuestResponse guest = guestClient.getGuestById(invoice.guestId());
        final DocumentType docType = invoice.documentType() != null
                ? invoice.documentType() : DocumentType.FATTURA;

        final Map<String, Object> context = buildContext(invoice, hotel, guest, docType);
        final byte[] pdf = pdfTemplateRenderer.render(templateFor(docType), context);
        log.info("PDF generated for invoice {} — {} bytes", invoiceId, pdf.length);
        return pdf;
    }

    private static String templateFor(final DocumentType docType) {
        return docType == DocumentType.FATTURA ? TEMPLATE_FATTURA : TEMPLATE_RICEVUTA;
    }

    private Map<String, Object> buildContext(final InvoiceResponse invoice, final HotelSettingsResponse hotel,
            final GuestResponse guest, final DocumentType docType) {
        final Map<String, Object> context = new HashMap<>();
        context.put("logoDataUri", loadLogoDataUri());
        context.put("hotelName", hotel.hotelName() != null ? hotel.hotelName() : "Hotel");
        context.put("hotelAddress", blankToNull(hotel.address()));
        context.put("hotelVat", blankToNull(hotel.vatNumber()));
        context.put("hotelFiscalCode", blankToNull(hotel.fiscalCode()));
        context.put("docTitle", docType == DocumentType.FATTURA ? "FATTURA" : "RICEVUTA");

        context.put("invoiceNumber", invoice.invoiceNumber());
        context.put("issueDate", invoice.issueDate() != null ? invoice.issueDate().format(DATE_FMT) : EMPTY_VALUE);
        context.put("status", invoice.status().name());

        final boolean hasCompany = guest.companyName() != null && !guest.companyName().isBlank();
        final String guestName = guest.firstName() + " " + guest.lastName();
        context.put("guestDisplayName", hasCompany ? guest.companyName() : guestName);
        context.put("guestPersonalName", hasCompany ? guestName : null);
        context.put("guestFiscalCode", blankToNull(guest.fiscalCode()));
        context.put("guestVat", blankToNull(guest.vatNumber()));
        context.put("guestPec", blankToNull(guest.pecEmail()));

        context.put("charges", toChargeRows(invoice.charges()));
        context.put("payments", toPaymentRows(invoice.payments()));

        final BigDecimal paid = invoice.payments().stream()
                .map((@NonNull PaymentResponse pr) -> pr.amount())
                .reduce(BigDecimal.ZERO, (@NonNull BigDecimal a, @NonNull BigDecimal b) -> a.add(b));
        context.put("totalFormatted", formatAmount(invoice.totalAmount()));
        context.put("dueFormatted", formatAmount(invoice.totalAmount().subtract(paid)));
        if (docType == DocumentType.FATTURA) {
            context.put("vatBreakdown", toVatRows(invoice.charges()));
        }
        return context;
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Reads the dev-provided logo (see {@link #LOGO_CLASSPATH_RESOURCE}) and returns it
     * as a {@code data:} URI, or {@code null} if no logo file has been shipped — the
     * templates render without a logo in that case, never a broken image.
     *
     * @return the logo as a base64 data URI, or {@code null}
     */
    private String loadLogoDataUri() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(LOGO_CLASSPATH_RESOURCE)) {
            if (in == null) {
                return null;
            }
            return LOGO_DATA_URI_PREFIX + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (final IOException e) {
            log.warn("Failed to load PDF logo asset from classpath — rendering without a logo", e);
            return null;
        }
    }

    private List<Map<String, String>> toChargeRows(final List<ChargeResponse> charges) {
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final ChargeResponse charge : charges) {
            final Map<String, String> row = new HashMap<>();
            row.put("typeLabel", formatChargeType(charge.type().name()));
            row.put("description", charge.description() != null ? charge.description() : EMPTY_VALUE);
            row.put("vatLabel", charge.vatRate() != null ? vatRateToLabel(charge.vatRate()) : EMPTY_VALUE);
            row.put("amountFormatted", formatAmount(charge.amount()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> toPaymentRows(final List<PaymentResponse> payments) {
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final PaymentResponse payment : payments) {
            final Map<String, String> row = new HashMap<>();
            row.put("methodLabel", formatPaymentMethod(payment.paymentMethod().name()));
            row.put("dateFormatted",
                    payment.paymentDate() != null ? payment.paymentDate().format(DATETIME_FMT) : EMPTY_VALUE);
            row.put("reference", payment.transactionReference() != null ? payment.transactionReference() : EMPTY_VALUE);
            row.put("amountFormatted", formatAmount(payment.amount()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> toVatRows(final List<ChargeResponse> charges) {
        final Map<BigDecimal, BigDecimal[]> breakdown = computeVatBreakdown(charges);
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final Map.Entry<BigDecimal, BigDecimal[]> entry : breakdown.entrySet()) {
            final Map<String, String> row = new HashMap<>();
            row.put("rateLabel", vatRateToLabel(entry.getKey()));
            row.put("taxableFormatted", formatAmount(entry.getValue()[0]));
            row.put("vatFormatted", formatAmount(entry.getValue()[1]));
            rows.add(row);
        }
        return rows;
    }

    private static Map<BigDecimal, BigDecimal[]> computeVatBreakdown(final List<ChargeResponse> charges) {
        final Map<BigDecimal, BigDecimal[]> breakdown = new TreeMap<>();
        if (charges == null) {
            return breakdown;
        }
        for (final ChargeResponse charge : charges) {
            if (charge.vatRate() == null || charge.amount() == null) {
                continue;
            }
            final BigDecimal rate = charge.vatRate();
            final BigDecimal amount = charge.amount();
            final BigDecimal taxable = amount.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
            final BigDecimal vat = amount.subtract(taxable);
            final BigDecimal[] group = breakdown.computeIfAbsent(rate,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            group[0] = group[0].add(taxable);
            group[1] = group[1].add(vat);
        }
        return breakdown;
    }

    private static String vatRateToLabel(final BigDecimal rate) {
        return rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    private String formatAmount(final BigDecimal amount) {
        if (amount == null) {
            return "EUR 0,00";
        }
        return String.format("EUR %,.2f", amount);
    }

    private String formatChargeType(final String type) {
        return switch (type) {
            case "ROOM_NIGHT" -> "Camera";
            case "FB_ORDER" -> "F&B";
            case "EXTRA" -> "Extra";
            default -> type;
        };
    }

    private String formatPaymentMethod(final String method) {
        return switch (method) {
            case "CASH" -> "Contanti";
            case "CREDIT_CARD" -> "Carta credito";
            case "DEBIT_CARD" -> "Carta debito";
            case "BANK_TRANSFER" -> "Bonifico";
            case "CHECK" -> "Assegno";
            default -> method;
        };
    }
}
