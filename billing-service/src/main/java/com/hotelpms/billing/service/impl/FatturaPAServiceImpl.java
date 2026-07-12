package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.HotelSettingsClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.service.FatturaPAService;
import com.hotelpms.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.hotelpms.billing.domain.PaymentMethod;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates FatturaPA FPR12 XML using the Java DOM API.
 * Hotel and guest data are fetched from the respective Feign clients.
 * The generated XML is conformant to the schema published by Agenzia delle Entrate
 * (namespace {@code http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public final class FatturaPAServiceImpl implements FatturaPAService {

    private static final String AE_NS =
            "http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2";
    private static final String FORMAT_TRASMISSIONE = "FPR12";
    private static final String TIPO_DOCUMENTO = "TD01";
    private static final String DIVISA = "EUR";
    private static final String NAZIONE_IT = "IT";
    private static final String CAP_PLACEHOLDER = "00000";
    private static final String COMUNE_PLACEHOLDER = "-";
    private static final String REGIME_FISCALE = "RF01";
    private static final String COND_PAGAMENTO = "TP02";
    private static final String ESIGIBILITA_IVA = "I";
    private static final String ID_PAESE_TAG = "IdPaese";
    private static final String ID_CODICE_TAG = "IdCodice";
    private static final String DEFAULT_MP = "MP05";
    private static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("0.10");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int INDIRIZZO_MAX_LEN = 60;

    private final InvoiceService invoiceService;
    private final HotelSettingsClient hotelSettingsClient;
    private final GuestClient guestClient;

    @Override
    public byte[] generateXml(@NonNull final UUID invoiceId) {
        log.info("Generating FatturaPA XML for invoice {}", invoiceId);
        final InvoiceResponse invoice = invoiceService.getInvoice(invoiceId);
        validateEligibility(invoice);

        final HotelSettingsResponse hotel = hotelSettingsClient.getSettings();
        final GuestResponse guest = guestClient.getGuestById(invoice.guestId());

        try {
            final Document doc = buildDocument(invoice, hotel, guest);
            return serialize(doc);
        } catch (ParserConfigurationException | TransformerException ex) {
            log.error("Failed to build FatturaPA XML for invoice {}", invoiceId, ex);
            throw new IllegalStateException("FATTURAPA_XML_BUILD_FAILED", ex);
        }
    }

    private static void validateEligibility(final InvoiceResponse invoice) {
        if (invoice.status() == InvoiceStatus.CANCELLED) {
            throw new InvoiceConflictException("CANNOT_EXPORT_CANCELLED_INVOICE");
        }
        if (invoice.documentType() != DocumentType.FATTURA) {
            throw new InvoiceConflictException("SDI_ONLY_VALID_FOR_FATTURA");
        }
    }

    private Document buildDocument(final InvoiceResponse invoice,
                                    final HotelSettingsResponse hotel,
                                    final GuestResponse guest)
            throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final Document doc = builder.newDocument();

        final Element root = doc.createElementNS(AE_NS, "p:FatturaElettronica");
        root.setAttribute("versione", FORMAT_TRASMISSIONE);
        root.setAttribute("xmlns:p", AE_NS);
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        doc.appendChild(root);

        root.appendChild(buildHeader(doc, invoice, hotel, guest));
        root.appendChild(buildBody(doc, invoice));
        return doc;
    }

    private Element buildHeader(final Document doc, final InvoiceResponse invoice,
                                 final HotelSettingsResponse hotel, final GuestResponse guest) {
        final Element header = doc.createElement("FatturaElettronicaHeader");
        header.appendChild(buildDatiTrasmissione(doc, invoice, hotel, guest));
        header.appendChild(buildCedentePrestatore(doc, hotel));
        header.appendChild(buildCessionarioCommittente(doc, guest));
        return header;
    }

    private Element buildDatiTrasmissione(final Document doc, final InvoiceResponse invoice,
                                           final HotelSettingsResponse hotel,
                                           final GuestResponse guest) {
        final Element dt = doc.createElement("DatiTrasmissione");
        final Element idTrasmittente = doc.createElement("IdTrasmittente");
        idTrasmittente.appendChild(el(doc, ID_PAESE_TAG, NAZIONE_IT));
        final String idCodice = sanitize(hotel.vatNumber(), "HOTELPMS");
        idTrasmittente.appendChild(el(doc, ID_CODICE_TAG, idCodice));
        dt.appendChild(idTrasmittente);

        final String progressivo = invoice.invoiceNumber() != null
                ? invoice.invoiceNumber().replace("/", "") : "00001";
        dt.appendChild(el(doc, "ProgressivoInvio", progressivo));
        dt.appendChild(el(doc, "FormatoTrasmissione", FORMAT_TRASMISSIONE));

        final String sdiCode = (guest != null && guest.sdiCode() != null && !guest.sdiCode().isBlank())
                ? guest.sdiCode() : "0000000";
        dt.appendChild(el(doc, "CodiceDestinatario", sdiCode));

        if ("0000000".equals(sdiCode) && guest != null
                && guest.pecEmail() != null && !guest.pecEmail().isBlank()) {
            dt.appendChild(el(doc, "PECDestinatario", guest.pecEmail()));
        }
        return dt;
    }

    private Element buildCedentePrestatore(final Document doc, final HotelSettingsResponse hotel) {
        final Element cedente = doc.createElement("CedentePrestatore");
        final Element datiAna = doc.createElement("DatiAnagrafici");

        final Element idFiscaleIVA = doc.createElement("IdFiscaleIVA");
        idFiscaleIVA.appendChild(el(doc, ID_PAESE_TAG, NAZIONE_IT));
        idFiscaleIVA.appendChild(el(doc, ID_CODICE_TAG, sanitize(hotel.vatNumber(), "00000000000")));
        datiAna.appendChild(idFiscaleIVA);

        if (hotel.fiscalCode() != null && !hotel.fiscalCode().isBlank()) {
            datiAna.appendChild(el(doc, "CodiceFiscale", hotel.fiscalCode()));
        }

        final Element anagrafica = doc.createElement("Anagrafica");
        anagrafica.appendChild(el(doc, "Denominazione", sanitize(hotel.hotelName(), "Hotel")));
        datiAna.appendChild(anagrafica);
        datiAna.appendChild(el(doc, "RegimeFiscale", REGIME_FISCALE));
        cedente.appendChild(datiAna);
        cedente.appendChild(buildSede(doc, truncate(hotel.address(), INDIRIZZO_MAX_LEN)));
        return cedente;
    }

    private Element buildCessionarioCommittente(final Document doc, final GuestResponse guest) {
        final Element cessionario = doc.createElement("CessionarioCommittente");
        final Element datiAna = doc.createElement("DatiAnagrafici");

        if (guest != null && guest.vatNumber() != null && !guest.vatNumber().isBlank()) {
            final Element idFiscaleIVA = doc.createElement("IdFiscaleIVA");
            idFiscaleIVA.appendChild(el(doc, ID_PAESE_TAG, NAZIONE_IT));
            idFiscaleIVA.appendChild(el(doc, ID_CODICE_TAG, guest.vatNumber()));
            datiAna.appendChild(idFiscaleIVA);
        }
        if (guest != null && guest.fiscalCode() != null && !guest.fiscalCode().isBlank()) {
            datiAna.appendChild(el(doc, "CodiceFiscale", guest.fiscalCode()));
        }

        final Element anagrafica = doc.createElement("Anagrafica");
        final String denominazione = guestDenominazione(guest);
        anagrafica.appendChild(el(doc, "Denominazione", denominazione));
        datiAna.appendChild(anagrafica);
        cessionario.appendChild(datiAna);
        cessionario.appendChild(buildSede(doc, "-"));
        return cessionario;
    }

    private static Element buildSede(final Document doc, final String indirizzo) {
        final Element sede = doc.createElement("Sede");
        sede.appendChild(el(doc, "Indirizzo", indirizzo.isBlank() ? "-" : indirizzo));
        sede.appendChild(el(doc, "CAP", CAP_PLACEHOLDER));
        sede.appendChild(el(doc, "Comune", COMUNE_PLACEHOLDER));
        sede.appendChild(el(doc, "Nazione", NAZIONE_IT));
        return sede;
    }

    private Element buildBody(final Document doc, final InvoiceResponse invoice) {
        final Element body = doc.createElement("FatturaElettronicaBody");
        body.appendChild(buildDatiGenerali(doc, invoice));
        body.appendChild(buildDatiBeniServizi(doc, invoice.charges()));
        body.appendChild(buildDatiPagamento(doc, invoice.payments(), invoice.totalAmount()));
        return body;
    }

    private Element buildDatiGenerali(final Document doc, final InvoiceResponse invoice) {
        final Element datiGenerali = doc.createElement("DatiGenerali");
        final Element dgd = doc.createElement("DatiGeneraliDocumento");
        dgd.appendChild(el(doc, "TipoDocumento", TIPO_DOCUMENTO));
        dgd.appendChild(el(doc, "Divisa", DIVISA));
        final String data = invoice.issueDate() != null
                ? invoice.issueDate().format(DATE_FMT) : "2000-01-01";
        dgd.appendChild(el(doc, "Data", data));
        dgd.appendChild(el(doc, "Numero", sanitize(invoice.invoiceNumber(), "0")));
        datiGenerali.appendChild(dgd);
        return datiGenerali;
    }

    private Element buildDatiBeniServizi(final Document doc, final List<ChargeResponse> charges) {
        final Element datiBeniServizi = doc.createElement("DatiBeniServizi");

        if (charges == null || charges.isEmpty()) {
            datiBeniServizi.appendChild(buildDettaglioLinea(doc, 1, "Soggiorno",
                    BigDecimal.ZERO, DEFAULT_VAT_RATE));
            final Element riepilogo = buildDatiRiepilogo(doc, DEFAULT_VAT_RATE,
                    BigDecimal.ZERO, BigDecimal.ZERO);
            datiBeniServizi.appendChild(riepilogo);
            return datiBeniServizi;
        }

        final Map<BigDecimal, BigDecimal[]> vatGroups = new LinkedHashMap<>();
        int lineNum = 1;
        for (final ChargeResponse charge : charges) {
            final BigDecimal vatRate = charge.vatRate() != null
                    ? charge.vatRate() : DEFAULT_VAT_RATE;
            final BigDecimal imponibile = imponibile(charge.amount(), vatRate);
            final BigDecimal imposta = charge.amount().subtract(imponibile).setScale(2, RoundingMode.HALF_UP);

            datiBeniServizi.appendChild(buildDettaglioLinea(doc, lineNum,
                    sanitize(charge.description(), "Prestazione"), charge.amount(), vatRate));
            lineNum++;

            vatGroups.merge(vatRate, new BigDecimal[]{imponibile, imposta},
                    (a, b) -> new BigDecimal[]{a[0].add(b[0]), a[1].add(b[1])});
        }

        for (final Map.Entry<BigDecimal, BigDecimal[]> entry : vatGroups.entrySet()) {
            datiBeniServizi.appendChild(buildDatiRiepilogo(doc, entry.getKey(),
                    entry.getValue()[0], entry.getValue()[1]));
        }
        return datiBeniServizi;
    }

    private static Element buildDettaglioLinea(final Document doc, final int numero,
                                                final String descrizione,
                                                final BigDecimal prezzoUnitario,
                                                final BigDecimal aliquotaIVA) {
        final Element linea = doc.createElement("DettaglioLinee");
        linea.appendChild(el(doc, "NumeroLinea", String.valueOf(numero)));
        linea.appendChild(el(doc, "Descrizione", descrizione));
        linea.appendChild(el(doc, "Quantita", "1.00"));
        linea.appendChild(el(doc, "PrezzoUnitario", formatDecimal(imponibile(prezzoUnitario, aliquotaIVA))));
        linea.appendChild(el(doc, "PrezzoTotale", formatDecimal(imponibile(prezzoUnitario, aliquotaIVA))));
        linea.appendChild(el(doc, "AliquotaIVA", formatPercent(aliquotaIVA)));
        return linea;
    }

    private static Element buildDatiRiepilogo(final Document doc, final BigDecimal aliquota,
                                               final BigDecimal imponibile, final BigDecimal imposta) {
        final Element riepilogo = doc.createElement("DatiRiepilogo");
        riepilogo.appendChild(el(doc, "AliquotaIVA", formatPercent(aliquota)));
        riepilogo.appendChild(el(doc, "ImponibileImporto", formatDecimal(imponibile)));
        riepilogo.appendChild(el(doc, "Imposta", formatDecimal(imposta)));
        riepilogo.appendChild(el(doc, "EsigibilitaIVA", ESIGIBILITA_IVA));
        return riepilogo;
    }

    private Element buildDatiPagamento(final Document doc, final List<PaymentResponse> payments,
                                        final BigDecimal totalAmount) {
        final Element datiPagamento = doc.createElement("DatiPagamento");
        datiPagamento.appendChild(el(doc, "CondizioniPagamento", COND_PAGAMENTO));

        if (payments == null || payments.isEmpty()) {
            datiPagamento.appendChild(buildDettaglioPagamento(doc, DEFAULT_MP, totalAmount));
        } else {
            for (final PaymentResponse payment : payments) {
                final String codice = methodToCodice(payment.paymentMethod());
                datiPagamento.appendChild(buildDettaglioPagamento(doc, codice,
                        payment.amount()));
            }
        }
        return datiPagamento;
    }

    private static Element buildDettaglioPagamento(final Document doc,
                                                    final String modalita,
                                                    final BigDecimal importo) {
        final Element dp = doc.createElement("DettaglioPagamento");
        dp.appendChild(el(doc, "ModalitaPagamento", modalita));
        dp.appendChild(el(doc, "ImportoPagamento", formatDecimal(importo)));
        return dp;
    }

    private static byte[] serialize(final Document doc) throws TransformerException {
        final TransformerFactory tf = TransformerFactory.newInstance();
        final Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    private static Element el(final Document doc, final String name, final String value) {
        final Element e = doc.createElement(name);
        e.setTextContent(value);
        return e;
    }

    private static String sanitize(final String value, final String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static String truncate(final String value, final int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String guestDenominazione(final GuestResponse guest) {
        if (guest == null) {
            return "Ospite";
        }
        if (guest.companyName() != null && !guest.companyName().isBlank()) {
            return guest.companyName();
        }
        final String first = sanitize(guest.firstName(), "");
        final String last = sanitize(guest.lastName(), "Ospite");
        return (first + " " + last).trim();
    }

    private static BigDecimal imponibile(final BigDecimal lordo, final BigDecimal aliquota) {
        if (lordo == null || lordo.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return lordo.divide(BigDecimal.ONE.add(aliquota), 2, RoundingMode.HALF_UP);
    }

    private static String formatDecimal(final BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatPercent(final BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String methodToCodice(final PaymentMethod method) {
        if (method == null) {
            return DEFAULT_MP;
        }
        return switch (method) {
            case CASH -> "MP01";
            case CREDIT_CARD -> "MP08";
            case TRANSFER -> DEFAULT_MP;
        };
    }
}
