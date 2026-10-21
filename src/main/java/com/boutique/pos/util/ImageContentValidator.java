package com.boutique.pos.util;

// El Content-Type que manda el cliente en un multipart (y la extensión del nombre de
// archivo) son datos que el propio cliente controla, así que no prueban nada: un .php o
// .jsp renombrado a foto.jpg trae igual Content-Type: image/jpeg si el atacante lo pone
// a mano. Lo único que no se puede falsificar sin que el archivo deje de ser una imagen
// real es la firma binaria (magic number) con la que arrancan los formatos de imagen —
// por eso la validación real se hace aquí, sobre los primeros bytes del archivo, y el
// resultado (no el header del cliente) es lo que se usa para aceptar/rechazar el archivo
// y para elegir la extensión con la que se guarda en disco.
public final class ImageContentValidator {

    private ImageContentValidator() {
    }

    /**
     * Detecta el tipo real de una imagen a partir de la firma binaria de sus primeros
     * bytes, ignorando el Content-Type declarado por el cliente.
     *
     * @param header primeros bytes del archivo (al menos 12 para reconocer WEBP)
     * @param length cuántos bytes de {@code header} son válidos (lo que realmente se leyó)
     * @return {@code "image/jpeg"}, {@code "image/png"}, {@code "image/webp"}, o
     *         {@code null} si no coincide con ninguna firma conocida
     */
    public static String detectContentType(byte[] header, int length) {
        if (length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return "image/png";
        }
        if (length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
