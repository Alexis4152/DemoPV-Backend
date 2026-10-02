package com.boutique.pos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra/descifra credenciales de integraciones externas guardadas en base de datos (hoy:
 * {@link com.boutique.pos.model.MailConfig#getSmtpPassword()}), para que no queden en texto
 * plano en un dump/backup/lectura directa de la base — hallazgo "Media" de la auditoría de
 * código. Se usa desde {@link EncryptedStringConverter}, no directo desde los services.
 *
 * <p>AES-256-GCM con la llave derivada (SHA-256) de {@code app.encryption.key} — mismo
 * patrón que {@code app.jwt.secret}: el valor real vive SOLO en la variable de entorno
 * {@code APP_ENCRYPTION_KEY} en producción, el default de aquí es solo para desarrollo
 * local. GCM ya trae autenticación integrada (detecta cualquier alteración del dato
 * cifrado), y un IV aleatorio por cada cifrado evita que dos valores iguales (ej. la misma
 * contraseña guardada dos veces) se vean igual una vez cifrados.</p>
 */
@Component
public class CredentialEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialEncryptionService(@Value("${app.encryption.key}") String secret) {
        this.key = deriveKey(secret);
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en cualquier JVM estándar — esto nunca debería
            // pasar en la práctica, pero AttributeConverter no puede declarar checked exceptions.
            throw new IllegalStateException("No se pudo derivar la llave de cifrado", e);
        }
    }

    /** Cifra un valor. {@code null} se conserva como {@code null} (no hay nada que cifrar). */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // El IV no es secreto — viaja junto al texto cifrado (prefijo) para poder
            // descifrar después; solo la llave AES debe mantenerse fuera de la base de datos.
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el valor", e);
        }
    }

    /** Descifra un valor previamente cifrado con {@link #encrypt}. {@code null} pasa igual. */
    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el valor", e);
        }
    }

    // GCM valida un tag de autenticación al descifrar: un valor que NO se cifró con esta
    // misma llave (ej. texto plano viejo, de antes de este fix) truena aquí en vez de
    // devolver basura silenciosamente — por eso sirve como detector confiable de "esto ya
    // estaba cifrado" para la migración de una sola vez (ver MailConfigPasswordEncryptionMigration).
    /** {@code true} si {@code value} descifra correctamente con la llave vigente. */
    public boolean looksEncrypted(String value) {
        try {
            decrypt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
