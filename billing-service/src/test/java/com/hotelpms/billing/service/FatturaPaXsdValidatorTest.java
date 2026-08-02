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
}
