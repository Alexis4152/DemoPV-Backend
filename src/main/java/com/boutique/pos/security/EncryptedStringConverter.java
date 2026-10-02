package com.boutique.pos.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Aplica {@link CredentialEncryptionService} de forma transparente en el límite del ORM:
 * cualquier campo anotado con {@code @Convert(converter = EncryptedStringConverter.class)}
 * queda cifrado en la columna de la base de datos y descifrado automáticamente al leerlo
 * como entidad, sin que cada service que lo toque (hoy {@code MailConfigService},
 * {@code MailConfigDataInitializer}) tenga que acordarse de cifrar/descifrar a mano — así
 * un futuro punto de guardado nuevo no puede "olvidar" cifrar por accidente.
 *
 * <p>{@code @Component} (junto con {@code @Converter}) para que Hibernate lo resuelva desde
 * el contenedor de Spring en vez de instanciarlo él mismo con un constructor vacío — así
 * puede recibir {@link CredentialEncryptionService} por inyección (Spring Boot 3 lo soporta
 * de fábrica, sin configuración adicional).</p>
 */
@Converter
@Component
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final CredentialEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return (attribute == null || attribute.isBlank()) ? attribute : encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return (dbData == null || dbData.isBlank()) ? dbData : encryptionService.decrypt(dbData);
    }
}
