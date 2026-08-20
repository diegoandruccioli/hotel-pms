package com.hotelpms.billing.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FatturaPaXsdValidator} against the real, bundled Agenzia delle
 * Entrate FPR12 v1.2.3 schema — not a fake/simplified schema, the actual file consumed
 * in production. Uses minimal hand-built XML (not a full invoice) to isolate schema
 * conformance from {@code FatturaPAServiceImpl}'s own generation logic, which has its
 * own dedicated tests.
 */
class FatturaPaXsdValidatorTest {

    private static final String NS = "http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2";

    private final FatturaPaXsdValidator validator = new FatturaPaXsdValidator();

    @Test
    void shouldReturnNoErrorsForAStructurallyValidFatturaPaDocument() {
        final byte[] xml = minimalValidFattura();

        final List<String> errors = validator.validate(xml);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldReturnNoErrorsForADocumentWithAnOutOfVatScopeNaturaLine() {
        // E18 prep: a second DettaglioLinee/DatiRiepilogo pair at 0% VAT with a Natura
        // code (imposta di soggiorno's eventual shape) plus RiferimentoNormativo, in
        // the XSD-mandated element order — proves the schema itself accepts this
        // structure before FatturaPAServiceImpl ever emits it for a real charge.
        final byte[] xml = withNaturaLine(minimalValidFattura());

        final List<String> errors = validator.validate(xml);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldReturnErrorsWhenARequiredElementIsRenamed() {
        final byte[] xml = new String(minimalValidFattura(), StandardCharsets.UTF_8)
                .replace("<Numero>1/2026</Numero>", "<NumeroSbagliato>1/2026</NumeroSbagliato>")
                .getBytes(StandardCharsets.UTF_8);

        final List<String> errors = validator.validate(xml);

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("Numero");
    }

    @Test
    void shouldReturnErrorsForXmlThatIsNotFatturaPaAtAll() {
        final byte[] xml = "<NotAnInvoice/>".getBytes(StandardCharsets.UTF_8);

        final List<String> errors = validator.validate(xml);

        assertThat(errors).isNotEmpty();
    }

    /**
     * Finding #16 (security-report.md, LOW): a DOCTYPE referencing an external DTD
     * must fail cleanly (rejected by ACCESS_EXTERNAL_DTD), not attempt to fetch it —
     * an unauthenticated/blind SSRF vector if this ever gains an upload endpoint.
     */
    @Test
    void shouldRejectDoctypeWithExternalDtdInsteadOfFetchingIt() {
        final byte[] xml = ("<?xml version=\"1.0\"?>"
                + "<!DOCTYPE NotAnInvoice SYSTEM \"http://169.254.169.254/should-not-be-fetched.dtd\">"
                + "<NotAnInvoice/>").getBytes(StandardCharsets.UTF_8);

        final List<String> errors = validator.validate(xml);

        assertThat(errors).isNotEmpty();
    }

    /**
     * The smallest XML that satisfies every {@code minOccurs="1"} element in the schema
     * for a private-party invoice (FPR12) with one line item — built by hand against the
     * schema, not copied from {@code FatturaPAServiceImpl}, so this test does not simply
     * validate "whatever the generator happens to produce".
     *
     * @return UTF-8 encoded bytes of a minimal, schema-valid FatturaPA document
     */
    private static byte[] minimalValidFattura() {
        final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:FatturaElettronica xmlns:p=\"" + NS + "\" versione=\"FPR12\">"
                + "  <FatturaElettronicaHeader>"
                + "    <DatiTrasmissione>"
                + "      <IdTrasmittente><IdPaese>IT</IdPaese><IdCodice>00000000000</IdCodice></IdTrasmittente>"
                + "      <ProgressivoInvio>00001</ProgressivoInvio>"
                + "      <FormatoTrasmissione>FPR12</FormatoTrasmissione>"
                + "      <CodiceDestinatario>0000000</CodiceDestinatario>"
                + "    </DatiTrasmissione>"
                + "    <CedentePrestatore>"
                + "      <DatiAnagrafici>"
                + "        <IdFiscaleIVA><IdPaese>IT</IdPaese><IdCodice>00000000000</IdCodice></IdFiscaleIVA>"
                + "        <Anagrafica><Denominazione>Hotel Test</Denominazione></Anagrafica>"
                + "        <RegimeFiscale>RF01</RegimeFiscale>"
                + "      </DatiAnagrafici>"
                + "      <Sede><Indirizzo>Via Test 1</Indirizzo><CAP>00100</CAP>"
                + "<Comune>Roma</Comune><Provincia>RM</Provincia><Nazione>IT</Nazione></Sede>"
                + "    </CedentePrestatore>"
                + "    <CessionarioCommittente>"
                + "      <DatiAnagrafici><Anagrafica><Denominazione>Mario Rossi</Denominazione></Anagrafica></DatiAnagrafici>"
                + "      <Sede><Indirizzo>Via Roma 1</Indirizzo><CAP>00100</CAP>"
                + "<Comune>Roma</Comune><Provincia>RM</Provincia><Nazione>IT</Nazione></Sede>"
                + "    </CessionarioCommittente>"
                + "  </FatturaElettronicaHeader>"
                + "  <FatturaElettronicaBody>"
                + "    <DatiGenerali><DatiGeneraliDocumento>"
                + "      <TipoDocumento>TD01</TipoDocumento><Divisa>EUR</Divisa>"
                + "      <Data>2026-01-01</Data><Numero>1/2026</Numero>"
                + "    </DatiGeneraliDocumento></DatiGenerali>"
                + "    <DatiBeniServizi>"
                + "      <DettaglioLinee><NumeroLinea>1</NumeroLinea><Descrizione>Soggiorno</Descrizione>"
                + "<Quantita>1.00</Quantita><PrezzoUnitario>100.00</PrezzoUnitario>"
                + "<PrezzoTotale>100.00</PrezzoTotale><AliquotaIVA>10.00</AliquotaIVA></DettaglioLinee>"
                + "      <DatiRiepilogo><AliquotaIVA>10.00</AliquotaIVA><ImponibileImporto>100.00</ImponibileImporto>"
                + "<Imposta>10.00</Imposta><EsigibilitaIVA>I</EsigibilitaIVA></DatiRiepilogo>"
                + "    </DatiBeniServizi>"
                + "    <DatiPagamento><CondizioniPagamento>TP02</CondizioniPagamento>"
                + "      <DettaglioPagamento><ModalitaPagamento>MP05</ModalitaPagamento>"
                + "<ImportoPagamento>110.00</ImportoPagamento></DettaglioPagamento>"
                + "    </DatiPagamento>"
                + "  </FatturaElettronicaBody>"
                + "</p:FatturaElettronica>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Inserts a second {@code DettaglioLinee}/{@code DatiRiepilogo} pair (0% VAT,
     * {@code Natura} {@code N1}, {@code RiferimentoNormativo}) into
     * {@link #minimalValidFattura()}, in the element order the XSD requires
     * ({@code DettaglioLineeType}: ...AliquotaIVA, Ritenuta, Natura...;
     * {@code DatiRiepilogoType}: AliquotaIVA, Natura, ..., ImponibileImporto, Imposta,
     * EsigibilitaIVA, RiferimentoNormativo). Renumbers {@code NumeroLinea} to 2.
     *
     * @param base the minimal valid fixture to extend
     * @return the extended fixture, still schema-valid
     */
    private static byte[] withNaturaLine(final byte[] base) {
        final String extraDettaglioLinee = "<DettaglioLinee><NumeroLinea>2</NumeroLinea>"
                + "<Descrizione>Imposta di soggiorno</Descrizione><Quantita>1.00</Quantita>"
                + "<PrezzoUnitario>6.00</PrezzoUnitario><PrezzoTotale>6.00</PrezzoTotale>"
                + "<AliquotaIVA>0.00</AliquotaIVA><Natura>N1</Natura></DettaglioLinee>";
        final String extraDatiRiepilogo = "<DatiRiepilogo><AliquotaIVA>0.00</AliquotaIVA><Natura>N1</Natura>"
                + "<ImponibileImporto>6.00</ImponibileImporto><Imposta>0.00</Imposta>"
                + "<EsigibilitaIVA>I</EsigibilitaIVA>"
                + "<RiferimentoNormativo>Escluso ex art. 15 DPR 633/1972</RiferimentoNormativo></DatiRiepilogo>";
        return new String(base, StandardCharsets.UTF_8)
                .replace("</DettaglioLinee>", "</DettaglioLinee>" + extraDettaglioLinee)
                .replace("</DatiRiepilogo>", "</DatiRiepilogo>" + extraDatiRiepilogo)
                .getBytes(StandardCharsets.UTF_8);
    }
}
