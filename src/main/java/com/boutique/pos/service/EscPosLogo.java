package com.boutique.pos.service;

import com.boutique.pos.model.Tienda;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convierte el logo de la tienda en una imagen para la impresora térmica (comando ESC/POS
 * {@code GS v 0}, imagen de puntos en blanco y negro).
 *
 * <p>La térmica no imprime grises: el logo se pone sobre fondo blanco (por las
 * transparencias), se le recortan los márgenes blancos, se escala para que quepa en
 * {@link #MAX_WIDTH} x {@link #MAX_HEIGHT} puntos y se pasa a blanco y negro con tramado
 * (Floyd–Steinberg), para que los colores claros no desaparezcan. Un logo de líneas
 * sólidas sale nítido; uno con degradados o foto sale punteado.</p>
 *
 * <p>Solo se usa el logo propio de la tienda: si no subió uno, el ticket impreso sale sin
 * logo (no se imprime el de Nexora). Si el archivo no se puede leer (ej. WebP, que Java no
 * lee sin librerías extra), el ticket sale igual, sin logo.</p>
 *
 * <p>El resultado se guarda en memoria por archivo y fecha de modificación, para no
 * reconvertir la imagen en cada ticket; al subir otro logo cambia la llave.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscPosLogo {

    /** Ancho máximo en puntos: el papel de 58mm imprime 384 por renglón. */
    static final int MAX_WIDTH = 256;
    /** Alto máximo en puntos (~2 cm a 203 dpi), para no gastar papel de más. */
    static final int MAX_HEIGHT = 160;
    /** Un pixel con luminancia mayor a esto se considera margen blanco al recortar. */
    private static final int WHITE_LIMIT = 240;
    private static final int MAX_CACHE = 50;

    private final TiendaLogoFiles logoFiles;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    /**
     * Bytes ESC/POS para imprimir el logo de la tienda, o un arreglo vacío si no tiene
     * logo propio o no se pudo convertir.
     */
    public byte[] forTienda(Tienda tienda) {
        Path path = logoFiles.ownLogo(tienda).orElse(null);
        if (path == null) return new byte[0];
        try {
            String key = path.toAbsolutePath() + "|" + Files.getLastModifiedTime(path).toMillis() + "|" + Files.size(path);
            if (cache.size() > MAX_CACHE) cache.clear();
            return cache.computeIfAbsent(key, k -> render(path));
        } catch (Exception e) {
            log.warn("No se pudo preparar el logo para el ticket impreso: {}", e.getMessage());
            return new byte[0];
        }
    }

    private byte[] render(Path path) {
        try {
            BufferedImage src = ImageIO.read(path.toFile());
            if (src == null) {
                log.warn("Formato de logo no soportado para el ticket impreso: {}", path.getFileName());
                return new byte[0];
            }
            return raster(src);
        } catch (Exception e) {
            log.warn("No se pudo leer el logo para el ticket impreso: {}", e.getMessage());
            return new byte[0];
        }
    }

    /** Imagen a comando {@code GS v 0} (modo normal), o vacío si la imagen es toda blanca. */
    static byte[] raster(BufferedImage src) {
        // Fondo blanco: lo transparente de un PNG debe salir blanco, no negro.
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();

        // Recorta el margen blanco para que el logo use todo el espacio.
        int minX = rgb.getWidth(), minY = rgb.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < rgb.getHeight(); y++) {
            for (int x = 0; x < rgb.getWidth(); x++) {
                if (luminance(rgb.getRGB(x, y)) < WHITE_LIMIT) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return new byte[0];
        int cw = maxX - minX + 1, ch = maxY - minY + 1;

        double scale = Math.min((double) MAX_WIDTH / cw, (double) MAX_HEIGHT / ch);
        int w = Math.max(1, (int) Math.round(cw * scale));
        int h = Math.max(1, (int) Math.round(ch * scale));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        sg.drawImage(rgb, 0, 0, w, h, minX, minY, maxX + 1, maxY + 1, null);
        sg.dispose();

        // Blanco y negro con tramado Floyd–Steinberg.
        float[][] gray = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) gray[y][x] = luminance(scaled.getRGB(x, y));
        }
        int bytesPerRow = (w + 7) / 8;
        byte[] bits = new byte[bytesPerRow * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float old = gray[y][x];
                boolean black = old < 128;
                float err = old - (black ? 0 : 255);
                if (black) bits[y * bytesPerRow + x / 8] |= (byte) (0x80 >> (x % 8));
                if (x + 1 < w) gray[y][x + 1] += err * 7 / 16;
                if (y + 1 < h) {
                    if (x > 0) gray[y + 1][x - 1] += err * 3 / 16;
                    gray[y + 1][x] += err * 5 / 16;
                    if (x + 1 < w) gray[y + 1][x + 1] += err * 1 / 16;
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(bits.length + 8);
        out.write(0x1D); out.write(0x76); out.write(0x30); out.write(0x00); // GS v 0, modo normal
        out.write(bytesPerRow & 0xFF); out.write((bytesPerRow >> 8) & 0xFF);
        out.write(h & 0xFF); out.write((h >> 8) & 0xFF);
        out.writeBytes(bits);
        return out.toByteArray();
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (int) Math.round(0.299 * r + 0.587 * gr + 0.114 * b);
    }
}
