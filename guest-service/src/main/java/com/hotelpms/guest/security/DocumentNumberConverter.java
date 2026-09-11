package com.hotelpms.guest.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA attribute converter that transparently encrypts {@code documentNumber}
 * on write and decrypts it on read (E23, GDPR Art. 32). {@code autoApply =
 * false}: applied explicitly via {@code @Convert} only on the one field that
 * needs it, not silently to every {@code String} column in the entity.
 *
 * <p>Registered as a Spring bean ({@code @Component}) rather than relying on
 * JPA's own converter instantiation, so {@link DocumentNumberEncryptor}'s
 * {@code @Value}-injected key/salt are available — Spring Data JPA wires
 * {@code @Component}-annotated converters through its {@code
 * SpringBeanContainer} automatically, no extra configuration needed.
 */
@Converter(autoApply = false)
@Component
@RequiredArgsConstructor
public class DocumentNumberConverter implements AttributeConverter<String, String> {

    private final DocumentNumberEncryptor encryptor;

    /** {@inheritDoc} */
    @Override
    public String convertToDatabaseColumn(final String attribute) {
        return encryptor.encrypt(attribute);
    }

    /** {@inheritDoc} */
    @Override
    public String convertToEntityAttribute(final String dbData) {
        return encryptor.decrypt(dbData);
    }
}
