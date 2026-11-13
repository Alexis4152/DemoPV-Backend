package com.boutique.pos.service;

import com.boutique.pos.model.Tienda;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Ubica en disco el logo que subió una tienda (ver {@link TiendaLogoService}), para los
 * tickets: el PDF ({@link TicketPdfService}) y el impreso ({@link EscPosLogo}).
 */
@Component
public class TiendaLogoFiles {

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    /**
     * Archivo del logo propio de la tienda, si subió uno y sigue existiendo en disco.
     *
     * @param tienda tienda del ticket, o null
     * @return la ruta del archivo, o vacío si la tienda no tiene logo propio
     */
    public Optional<Path> ownLogo(Tienda tienda) {
        if (tienda == null || tienda.getLogoPath() == null || tienda.getLogoPath().isBlank()) {
            return Optional.empty();
        }
        Path path = Paths.get(uploadsDir, tienda.getLogoPath().replaceFirst("^/uploads/", ""));
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }
}
