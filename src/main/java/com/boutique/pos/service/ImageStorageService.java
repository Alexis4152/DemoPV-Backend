package com.boutique.pos.service;

import com.boutique.pos.util.ImageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento genérico de imágenes subidas en disco local, bajo {@code
 * app.uploads.dir}. Centraliza lo que antes estaba duplicado entre {@link
 * ProductImageService} (fotos de producto) y {@link TiendaLogoService} (logo de tienda):
 * validar tamaño y tipo real del archivo (ver {@link ImageContentValidator} — nunca se
 * confía en el {@code Content-Type} ni la extensión que manda el cliente), guardarlo con
 * nombre único, y borrar un archivo existente en disco de forma best-effort.
 */
@Service
@Slf4j
public class ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    /**
     * Valida y guarda una imagen subida.
     *
     * @param file archivo recibido en el multipart
     * @param subfolder carpeta destino relativa a {@code app.uploads.dir} (ej. {@code
     *                  "logos"} o {@code "products/42"})
     * @param filenamePrefix prefijo del nombre de archivo antes del UUID único (ej. {@code
     *                       "tienda-3-"}, o vacío)
     * @param maxSizeBytes tamaño máximo permitido, en bytes
     * @return la ruta pública del archivo guardado, servida vía {@code /uploads/**} (ej.
     *         {@code "/uploads/logos/tienda-3-<uuid>.png"})
     * @throws IllegalArgumentException si el archivo viene vacío, excede el tamaño máximo,
     *         o su contenido real no es PNG/JPEG/WEBP
     * @throws RuntimeException si falla la lectura o escritura del archivo
     */
    public String store(MultipartFile file, String subfolder, String filenamePrefix, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo de imagen");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("La imagen no debe pesar más de " + (maxSizeBytes / (1024 * 1024)) + " MB");
        }
        String realType = sniffContentType(file);
        if (realType == null || !ALLOWED_TYPES.contains(realType)) {
            throw new IllegalArgumentException("La imagen debe ser PNG, JPG o WEBP");
        }

        try {
            Path dir = Paths.get(uploadsDir, subfolder);
            Files.createDirectories(dir);
            String filename = filenamePrefix + UUID.randomUUID() + extensionFor(realType);
            file.transferTo(dir.resolve(filename));
            return "/uploads/" + subfolder + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar la imagen", e);
        }
    }

    /**
     * Borra un archivo previamente guardado, de forma best-effort: si no existe o falla
     * el borrado (permisos, etc.), solo se registra en el log — nunca interrumpe el flujo
     * que lo llama (ej. reemplazar un logo no debe fallar porque no se pudo borrar el
     * anterior).
     *
     * @param publicPath ruta pública del archivo ({@code /uploads/...}), o null/vacío si
     *                    no había ninguno
     */
    public void delete(String publicPath) {
        if (publicPath == null || publicPath.isBlank()) return;
        try {
            Files.deleteIfExists(Paths.get(uploadsDir, publicPath.replaceFirst("^/uploads/", "")));
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo de imagen ({}): {}", publicPath, e.getMessage());
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /**
     * Determina el tipo real de la imagen leyendo la firma binaria de sus primeros bytes
     * (ver {@link ImageContentValidator}), en vez de confiar en el Content-Type que manda
     * el cliente (falsificable con solo cambiar un header).
     */
    private String sniffContentType(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer la imagen", e);
        }
        return ImageContentValidator.detectContentType(header, read);
    }
}
