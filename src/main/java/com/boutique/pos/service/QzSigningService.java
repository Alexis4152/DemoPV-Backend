package com.boutique.pos.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Firma las conexiones que el frontend hace a QZ Tray para imprimir tickets (ver
 * {@code utils/printer.js} del frontend), usando el par certificado/llave privada
 * generado para esta instalación ({@code qz-keys/} en la raíz del proyecto backend, fuera
 * de git — ver {@code .gitignore}).
 * <p>
 * Sin esto, QZ Tray trata cada conexión como "anónima": no puede recordar la
 * autorización del cajero y le pide permiso en cada venta, sin posibilidad real de marcar
 * "recordar esta decisión" (el checkbox aparece deshabilitado). El certificado puede ser
 * autofirmado — no hace falta comprarlo a QZ — eso solo hace que QZ Tray muestre "sitio no
 * confiable" en vez del ícono verde de confianza total, pero SÍ le permite al cajero
 * recordar la autorización de forma permanente, que es lo que realmente resuelve la
 * molestia de tener que autorizar en cada impresión.
 */
@Service
@Slf4j
public class QzSigningService {

    @Value("${app.qz.certificate-path}")
    private String certificatePath;

    @Value("${app.qz.private-key-path}")
    private String privateKeyPath;

    private String certificatePem;
    private PrivateKey privateKey;

    /**
     * Carga el certificado y la llave privada al arrancar. Si los archivos no existen
     * (instalación nueva sin el par generado todavía), solo deja un aviso en el log: la
     * impresión de tickets sigue funcionando igual, simplemente QZ Tray seguirá pidiendo
     * autorización en cada venta hasta que se generen y configuren estos archivos.
     */
    @PostConstruct
    void load() {
        try {
            certificatePem = Files.readString(Path.of(certificatePath));
            String keyPem = Files.readString(Path.of(privateKeyPath))
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyPem);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            log.info("Certificado de firma de QZ Tray cargado desde {}", certificatePath);
        } catch (Exception e) {
            log.warn("No se pudo cargar el certificado/llave de QZ Tray ({}): {}. La impresion de tickets sigue funcionando, pero QZ Tray pedira autorizacion en cada venta.",
                    certificatePath, e.getMessage());
        }
    }

    /** Certificado público en PEM, para que el frontend lo mande vía {@code qz.security.setCertificatePromise}. */
    public String getCertificate() {
        return certificatePem;
    }

    /**
     * Firma {@code data} (el texto que QZ Tray le pasa a {@code qz.security.setSignaturePromise})
     * con SHA512withRSA, algoritmo configurado del lado del frontend vía
     * {@code qz.security.setSignatureAlgorithm('SHA512')} para que ambos lados coincidan.
     */
    public String sign(String data) {
        try {
            Signature signature = Signature.getInstance("SHA512withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la solicitud de QZ Tray", e);
        }
    }
}
