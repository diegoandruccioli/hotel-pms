package com.hotelpms.billing.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a generated FatturaPA XML byte array against the official schema
 * published by Agenzia delle Entrate (FPR12, v1.2.3 — valid since 2026-04-01,
 * {@code https://www.fatturapa.gov.it/}). Schema files are bundled under
 * {@code src/main/resources/xsd/}, not fetched at runtime: the FatturaPA
 * schema {@code <xs:import>}s the W3C XML-DSig schema from a live
 * {@code http://www.w3.org} URL, which would make every XML generation
 * depend on an external site being reachable — {@link #resourceResolver()}
 * redirects that import to the bundled copy instead.
 *
 * <p>{@link Schema} is thread-safe and built once; {@link Validator} is not,
 * so a fresh one is created per call (cheap — it wraps the pre-compiled
 * {@link Schema}, it does not recompile it).
 */
@Component
public class FatturaPaXsdValidator {

    private static final String FATTURAPA_XSD = "xsd/Schema_VFPR12_v1.2.3.xsd";
    private static final String XMLDSIG_XSD = "xsd/xmldsig-core-schema.xsd";
    private static final String XMLDSIG_NAMESPACE = "http://www.w3.org/2000/09/xmldsig#";

    private final Schema schema;

    /**
     * Compiles the bundled FatturaPA schema once at construction.
     *
     * @throws IllegalStateException if the bundled schema files are missing or invalid —
     *                                a deployment defect, not a per-request condition
     */
    public FatturaPaXsdValidator() {
        try {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver(resourceResolver());
            try (InputStream xsd = new ClassPathResource(FATTURAPA_XSD).getInputStream()) {
                this.schema = factory.newSchema(new StreamSource(xsd));
            }
        } catch (final IOException | SAXException ex) {
            // Fails application startup rather than at first invoice export — a broken
            // bundled schema is a deployment defect, not a per-request condition.
            throw new IllegalStateException("FATTURAPA_XSD_SCHEMA_LOAD_FAILED", ex);
        }
    }

    /**
     * Validates the given FatturaPA XML against the bundled schema.
     *
     * @param xml the serialized XML to validate
     * @return the list of validation error messages, empty if the XML is schema-valid
     */
    public List<String> validate(final byte[] xml) {
        final List<String> errors = new ArrayList<>();
        final Validator validator = schema.newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(final SAXParseException exception) {
                // schema warnings (e.g. unused import) are not export-blocking
            }

            @Override
            public void error(final SAXParseException exception) {
                errors.add("line " + exception.getLineNumber() + ": " + exception.getMessage());
            }

            @Override
            public void fatalError(final SAXParseException exception) {
                errors.add("line " + exception.getLineNumber() + ": " + exception.getMessage());
            }
        });
        try {
            validator.validate(new StreamSource(new java.io.ByteArrayInputStream(xml)));
        } catch (final SAXException | IOException ex) {
            errors.add(ex.getMessage());
        }
        return errors;
    }

    private static LSResourceResolver resourceResolver() {
        return (type, namespaceURI, publicId, systemId, baseURI) -> {
            if (!XMLDSIG_NAMESPACE.equals(namespaceURI)) {
                return null;
            }
            return new ClassPathResourceLsInput(publicId, systemId, baseURI);
        };
    }

    /**
     * Minimal {@link LSInput} backed by the bundled xmldsig-core-schema.xsd classpath
     * resource — the only import the FatturaPA schema needs resolved locally.
     */
    private static final class ClassPathResourceLsInput implements LSInput {
        private final String publicId;
        private final String systemId;
        private final String baseUri;

        ClassPathResourceLsInput(final String publicId, final String systemId, final String baseUri) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseUri = baseUri;
        }

        @Override
        public InputStream getByteStream() {
            try {
                return new ClassPathResource(XMLDSIG_XSD).getInputStream();
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public String getBaseURI() {
            return baseUri;
        }

        @Override
        public java.io.Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(final java.io.Reader characterStream) {
            // not used — byte stream only
        }

        @Override
        public void setByteStream(final InputStream byteStream) {
            // not used — resolved from classpath, not settable
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setStringData(final String stringData) {
            // not used — byte stream only
        }

        @Override
        public void setSystemId(final String systemId) {
            // immutable for this resolver's purposes
        }

        @Override
        public void setPublicId(final String publicId) {
            // immutable for this resolver's purposes
        }

        @Override
        public void setBaseURI(final String baseURI) {
            // immutable for this resolver's purposes
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public void setEncoding(final String encoding) {
            // not used
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(final boolean certifiedText) {
            // not used
        }
    }
}
