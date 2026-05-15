package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.HotelSettingsClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.PdfInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Generates A4 PDF invoices using Apache PDFBox 3.x.
 * Hotel header data is fetched from stay-service; guest name from guest-service.
 * Both Feign calls use circuit-breaker fallbacks so PDF generation never blocks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfInvoiceServiceImpl implements PdfInvoiceService {

    // Layout constants (A4 = 595×842 pt, origin at bottom-left)
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float MARGIN = 50f;
    private static final float RIGHT_EDGE = PAGE_WIDTH - MARGIN;
    private static final float CONTENT_WIDTH = RIGHT_EDGE - MARGIN;
    private static final float TOP_Y = PAGE_HEIGHT - MARGIN;

    private static final float FONT_SIZE_TITLE = 15f;
    private static final float FONT_SIZE_SECTION = 10f;
    private static final float FONT_SIZE_BODY = 9f;
    private static final float FONT_SIZE_SMALL = 8f;

    private static final float LINE_HEIGHT = 13f;
    private static final float SECTION_GAP = 18f;
    private static final float RULE_OFFSET = 4f;
    private static final float LINE_WIDTH_THIN = 0.5f;

    // Charges table column X positions
    private static final float COL_TYPE_X = MARGIN;
    private static final float COL_DESC_X = MARGIN + 90f;
    private static final float COL_AMT_X = RIGHT_EDGE;

    // Payments table column X positions
    private static final float COL_PAY_METHOD_X = MARGIN;
    private static final float COL_PAY_DATE_X = MARGIN + 110f;
    private static final float COL_PAY_REF_X = MARGIN + 240f;
    private static final float COL_PAY_AMT_X = RIGHT_EDGE;

    private static final float LABEL_X_RIGHT = RIGHT_EDGE - 120f;
    private static final float LABEL_VALUE_OFFSET = 70f;

    private static final int MAX_CHARGE_DESC_LEN = 38;
    private static final int MAX_PAYMENT_REF_LEN = 15;

    private static final String EMPTY_VALUE = "---";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InvoiceService invoiceService;
    private final HotelSettingsClient hotelSettingsClient;
    private final GuestClient guestClient;

    /** {@inheritDoc} */
    @Override
    public byte[] generateInvoicePdf(final UUID invoiceId) {
        log.info("Generating PDF for invoice {}", invoiceId);
        final InvoiceResponse invoice = invoiceService.getInvoice(invoiceId);
        final HotelSettingsResponse hotel = hotelSettingsClient.getSettings();
        final GuestResponse guest = guestClient.getGuestById(invoice.guestId());

        try (PDDocument doc = new PDDocument()) {
            final PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                buildContent(cs, invoice, hotel, guest);
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            log.info("PDF generated for invoice {} — {} bytes", invoiceId, out.size());
            return out.toByteArray();
        } catch (final IOException e) {
            throw new IllegalStateException("PDF_GENERATION_FAILED", e);
        }
    }

    private void buildContent(final PDPageContentStream cs,
                              final InvoiceResponse invoice,
                              final HotelSettingsResponse hotel,
                              final GuestResponse guest) throws IOException {
        final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        float y = TOP_Y;
        y = drawHotelHeader(cs, hotel, bold, regular, y);
        drawHorizontalLine(cs, y);
        y -= SECTION_GAP;
        y = drawInvoiceMeta(cs, invoice, guest, bold, regular, y);
        drawHorizontalLine(cs, y);
        y -= SECTION_GAP;
        y = drawChargesSection(cs, invoice.charges(), bold, regular, y);
        drawHorizontalLine(cs, y);
        y -= SECTION_GAP;
        y = drawPaymentsSection(cs, invoice.payments(), bold, regular, y);
        drawHorizontalLine(cs, y);
        y -= SECTION_GAP;
        final BigDecimal paid = invoice.payments().stream()
                .map(PaymentResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        drawTotals(cs, invoice.totalAmount(), paid, bold, regular, y);
    }

    private float drawHotelHeader(final PDPageContentStream cs,
                                   final HotelSettingsResponse hotel,
                                   final PDType1Font bold,
                                   final PDType1Font regular,
                                   final float startY) throws IOException {
        float y = startY;
        final String name = hotel.hotelName() != null ? hotel.hotelName() : "Hotel";
        drawText(cs, bold, FONT_SIZE_TITLE, MARGIN, y, name);
        drawRightAlignedText(cs, bold, FONT_SIZE_TITLE, RIGHT_EDGE, y, "FATTURA");
        y -= LINE_HEIGHT + 2f;

        if (hotel.address() != null && !hotel.address().isBlank()) {
            drawText(cs, regular, FONT_SIZE_BODY, MARGIN, y, hotel.address());
            y -= LINE_HEIGHT;
        }
        if (hotel.vatNumber() != null && !hotel.vatNumber().isBlank()) {
            drawText(cs, regular, FONT_SIZE_BODY, MARGIN, y, "P.IVA: " + hotel.vatNumber());
            y -= LINE_HEIGHT;
        }
        if (hotel.fiscalCode() != null && !hotel.fiscalCode().isBlank()) {
            drawText(cs, regular, FONT_SIZE_BODY, MARGIN, y, "C.F.: " + hotel.fiscalCode());
            y -= LINE_HEIGHT;
        }
        return y - RULE_OFFSET;
    }

    private float drawInvoiceMeta(final PDPageContentStream cs,
                                   final InvoiceResponse invoice,
                                   final GuestResponse guest,
                                   final PDType1Font bold,
                                   final PDType1Font regular,
                                   final float startY) throws IOException {
        float y = startY;
        drawText(cs, bold, FONT_SIZE_SECTION, MARGIN, y, "N. Fattura:");
        drawText(cs, regular, FONT_SIZE_SECTION, MARGIN + LABEL_VALUE_OFFSET, y, invoice.invoiceNumber());
        drawText(cs, bold, FONT_SIZE_SECTION, LABEL_X_RIGHT, y, "Intestatario:");
        y -= LINE_HEIGHT;

        final String dateStr = invoice.issueDate() != null
                ? invoice.issueDate().format(DATE_FMT) : EMPTY_VALUE;
        drawText(cs, bold, FONT_SIZE_SECTION, MARGIN, y, "Data:");
        drawText(cs, regular, FONT_SIZE_SECTION, MARGIN + LABEL_VALUE_OFFSET, y, dateStr);

        final String guestName = guest.firstName() + " " + guest.lastName();
        drawText(cs, regular, FONT_SIZE_SECTION, LABEL_X_RIGHT, y, guestName);
        y -= LINE_HEIGHT;

        drawText(cs, bold, FONT_SIZE_SECTION, MARGIN, y, "Stato:");
        drawText(cs, regular, FONT_SIZE_SECTION, MARGIN + LABEL_VALUE_OFFSET, y, invoice.status().name());
        return y - LINE_HEIGHT - RULE_OFFSET;
    }

    private float drawChargesSection(final PDPageContentStream cs,
                                      final List<ChargeResponse> charges,
                                      final PDType1Font bold,
                                      final PDType1Font regular,
                                      final float startY) throws IOException {
        float y = startY;
        drawText(cs, bold, FONT_SIZE_SECTION, MARGIN, y, "ADDEBITI");
        y -= LINE_HEIGHT;

        drawText(cs, bold, FONT_SIZE_SMALL, COL_TYPE_X, y, "Tipo");
        drawText(cs, bold, FONT_SIZE_SMALL, COL_DESC_X, y, "Descrizione");
        drawRightAlignedText(cs, bold, FONT_SIZE_SMALL, COL_AMT_X, y, "Importo");
        y -= LINE_HEIGHT;

        final List<ChargeResponse> rows = charges != null ? charges : List.of();
        for (final ChargeResponse charge : rows) {
            final String typeName = formatChargeType(charge.type().name());
            final String desc = charge.description() != null ? charge.description() : EMPTY_VALUE;
            drawText(cs, regular, FONT_SIZE_BODY, COL_TYPE_X, y, typeName);
            drawText(cs, regular, FONT_SIZE_BODY, COL_DESC_X, y, truncate(desc, MAX_CHARGE_DESC_LEN));
            drawRightAlignedText(cs, regular, FONT_SIZE_BODY, COL_AMT_X, y,
                    formatAmount(charge.amount()));
            y -= LINE_HEIGHT;
        }
        if (rows.isEmpty()) {
            drawText(cs, regular, FONT_SIZE_BODY, MARGIN, y, "Nessun addebito");
            y -= LINE_HEIGHT;
        }
        return y - RULE_OFFSET;
    }

    private float drawPaymentsSection(final PDPageContentStream cs,
                                       final List<PaymentResponse> payments,
                                       final PDType1Font bold,
                                       final PDType1Font regular,
                                       final float startY) throws IOException {
        float y = startY;
        drawText(cs, bold, FONT_SIZE_SECTION, MARGIN, y, "PAGAMENTI");
        y -= LINE_HEIGHT;

        drawText(cs, bold, FONT_SIZE_SMALL, COL_PAY_METHOD_X, y, "Metodo");
        drawText(cs, bold, FONT_SIZE_SMALL, COL_PAY_DATE_X, y, "Data");
        drawText(cs, bold, FONT_SIZE_SMALL, COL_PAY_REF_X, y, "Riferimento");
        drawRightAlignedText(cs, bold, FONT_SIZE_SMALL, COL_PAY_AMT_X, y, "Importo");
        y -= LINE_HEIGHT;

        final List<PaymentResponse> rows = payments != null ? payments : List.of();
        for (final PaymentResponse payment : rows) {
            final String method = formatPaymentMethod(payment.paymentMethod().name());
            final String date = payment.paymentDate() != null
                    ? payment.paymentDate().format(DATETIME_FMT) : EMPTY_VALUE;
            final String ref = payment.transactionReference() != null
                    ? payment.transactionReference() : EMPTY_VALUE;
            drawText(cs, regular, FONT_SIZE_BODY, COL_PAY_METHOD_X, y, method);
            drawText(cs, regular, FONT_SIZE_BODY, COL_PAY_DATE_X, y, date);
            drawText(cs, regular, FONT_SIZE_BODY, COL_PAY_REF_X, y, truncate(ref, MAX_PAYMENT_REF_LEN));
            drawRightAlignedText(cs, regular, FONT_SIZE_BODY, COL_PAY_AMT_X, y,
                    formatAmount(payment.amount()));
            y -= LINE_HEIGHT;
        }
        if (rows.isEmpty()) {
            drawText(cs, regular, FONT_SIZE_BODY, MARGIN, y, "Nessun pagamento");
            y -= LINE_HEIGHT;
        }
        return y - RULE_OFFSET;
    }

    private void drawTotals(final PDPageContentStream cs,
                             final BigDecimal total,
                             final BigDecimal paid,
                             final PDType1Font bold,
                             final PDType1Font regular,
                             final float startY) throws IOException {
        float y = startY;
        drawText(cs, bold, FONT_SIZE_SECTION, LABEL_X_RIGHT, y, "TOTALE:");
        drawRightAlignedText(cs, bold, FONT_SIZE_SECTION, RIGHT_EDGE, y, formatAmount(total));
        y -= LINE_HEIGHT;

        final BigDecimal due = total.subtract(paid);
        drawText(cs, regular, FONT_SIZE_SECTION, LABEL_X_RIGHT, y, "Da pagare:");
        drawRightAlignedText(cs, regular, FONT_SIZE_SECTION, RIGHT_EDGE, y, formatAmount(due));
    }

    private void drawHorizontalLine(final PDPageContentStream cs, final float y) throws IOException {
        cs.setLineWidth(LINE_WIDTH_THIN);
        cs.moveTo(MARGIN, y);
        cs.lineTo(RIGHT_EDGE, y);
        cs.stroke();
    }

    private void drawText(final PDPageContentStream cs, final PDType1Font font,
                           final float size, final float x, final float y,
                           final String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
    }

    private void drawRightAlignedText(final PDPageContentStream cs, final PDType1Font font,
                                       final float size, final float rightX, final float y,
                                       final String text) throws IOException {
        final String safe = sanitize(text);
        final float textWidth = font.getStringWidth(safe) / 1000f * size;
        drawText(cs, font, size, rightX - textWidth, y, safe);
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

    private String sanitize(final String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    private String truncate(final String text, final int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLen - 3) + "...";
    }
}
