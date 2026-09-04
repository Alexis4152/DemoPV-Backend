package com.boutique.pos.service;

import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleItem;
import com.boutique.pos.model.TiendaInfo;
import com.boutique.pos.repository.TiendaInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el ticket de una venta en formato ESC/POS: la secuencia de bytes (texto +
 * comandos de control) que entienden directamente las impresoras termicas de recibos, a
 * diferencia de {@link TicketPdfService}, que genera un PDF pensado para adjuntarse a un
 * correo.
 *
 * <p>Este ticket no lo imprime el backend: el navegador del cajero, con ayuda de un
 * puente local instalado en su propia computadora (ej. QZ Tray, que si puede hablarle a
 * un puerto USB), es quien manda estos bytes tal cual a la impresora fisica. El backend
 * solo construye el contenido, nunca toca hardware directamente, ya que corre en un
 * servidor en la nube sin ningun acceso a las impresoras de las tiendas.</p>
 *
 * <p>El resultado se entrega como una cadena hexadecimal (dos caracteres ASCII por byte,
 * ej. "1B40..."), no como texto con caracteres de control incrustados: los comandos de
 * control ESC/POS incluyen valores de byte que no son texto valido (ej. 0xFA para el
 * temporizador del cajon), y viajar como texto normal por JSON/HTTP arriesgaria que una
 * conversion de codificacion (UTF-8, etc.) los corrompiera antes de llegar a la
 * impresora. El hexadecimal es puro ASCII y no tiene ese riesgo.</p>
 *
 * <p>Pensado para papel termico de 58mm (32 caracteres por linea a fuente estandar); si
 * en el futuro se usa una impresora de 80mm, ajustar {@link #WIDTH}.</p>
 */
@Service
@RequiredArgsConstructor
public class EscPosTicketService {

    /** Ancho en caracteres de una linea, a fuente estandar, en papel termico de 58mm. */
    private static final int WIDTH = 32;

    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TiendaInfoRepository tiendaInfoRepository;

    /**
     * Arma el ticket completo de una venta y lo entrega como cadena hexadecimal, lista
     * para que el frontend se la pase tal cual a QZ Tray (o equivalente) como impresion
     * "raw" en formato hex.
     *
     * <p>La apertura del cajon se agrega al final SOLO si la venta fue en efectivo — no
     * tiene sentido abrir la caja en una venta con tarjeta o transferencia, donde no hay
     * dinero fisico que entregar de cambio.</p>
     *
     * @param sale venta ya registrada (con sus {@link SaleItem} cargados) a imprimir
     * @return el ticket completo codificado en hexadecimal
     */
    public String build(Sale sale) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        ctl(buf, 0x1B, 0x40); // ESC @ : inicializa la impresora

        ctl(buf, 0x1B, 0x61, 0x01); // alinear al centro
        ctl(buf, 0x1B, 0x45, 0x01); // negritas ON
        text(buf, wrap(sale.getTienda() != null ? sale.getTienda().getName() : "Punto de Venta") + "\n");
        ctl(buf, 0x1B, 0x45, 0x00); // negritas OFF

        TiendaInfo info = sale.getTienda() != null
                ? tiendaInfoRepository.findByTiendaId(sale.getTienda().getId()).orElse(null)
                : null;
        if (info != null && info.getRfc() != null && !info.getRfc().isBlank()) {
            text(buf, "RFC: " + info.getRfc() + "\n");
        }
        ctl(buf, 0x1B, 0x61, 0x00); // alinear a la izquierda
        text(buf, line());
        text(buf, "Folio: " + sale.getId() + "\n");
        if (sale.getCreatedAt() != null) {
            text(buf, DATE_FMT.format(sale.getCreatedAt()) + "\n");
        }
        if (sale.getUser() != null) {
            text(buf, "Atendio: " + wrap(sale.getUser().getName()) + "\n");
        }
        text(buf, line());

        for (SaleItem item : sale.getItems()) {
            text(buf, wrap(item.getProductName()) + "\n");
            String left = stripTrailingZeros(item.getQuantity()) + " x " + money(item.getUnitPrice());
            text(buf, padBetween(left, money(item.getSubtotal())) + "\n");
        }
        text(buf, line());

        text(buf, padBetween("Subtotal", money(sale.getSubtotal())) + "\n");
        if (sale.getDiscount() != null && sale.getDiscount().signum() > 0) {
            text(buf, padBetween("Descuento", "-" + money(sale.getDiscount())) + "\n");
        }
        if (sale.getTax() != null && sale.getTax().signum() > 0) {
            text(buf, padBetween("Impuestos", money(sale.getTax())) + "\n");
        }
        ctl(buf, 0x1B, 0x45, 0x01);
        text(buf, padBetween("TOTAL", money(sale.getTotal())) + "\n");
        ctl(buf, 0x1B, 0x45, 0x00);

        if (sale.getAmountReceived() != null) {
            text(buf, padBetween("Recibido", money(sale.getAmountReceived())) + "\n");
            text(buf, padBetween("Cambio", money(sale.getChangeGiven())) + "\n");
        }

        text(buf, line());
        ctl(buf, 0x1B, 0x61, 0x01);
        text(buf, "Gracias por su compra\n");
        ctl(buf, 0x1B, 0x61, 0x00);
        text(buf, "\n\n\n");
        ctl(buf, 0x1D, 0x56, 0x01); // GS V 1 : corte parcial del papel

        // Solo se abre el cajon en ventas en efectivo (ver Javadoc de build()).
        // ESC p m t1 t2 : pulso al pin 2 del conector RJ11 del cajon (m=0), con los
        // tiempos por default mas comunes (t1=25, t2=250, en unidades de ~2ms) que trae
        // practicamente cualquier cajon compatible.
        if (sale.getPaymentMethod() == PaymentMethod.CASH) {
            ctl(buf, 0x1B, 0x70, 0x00, 0x19, 0xFA);
        }

        return toHex(buf.toByteArray());
    }

    /** Agrega uno o mas bytes de control (valores 0-255) al ticket, tal cual, sin interpretarlos como texto. */
    private void ctl(ByteArrayOutputStream buf, int... codes) {
        for (int c : codes) buf.write(c);
    }

    /**
     * Agrega texto imprimible al ticket, codificado en ISO-8859-1 (Latin-1) — la
     * codificacion de un solo byte por caracter que la mayoria de impresoras termicas
     * ESC/POS esperan por default, y que representa correctamente los acentos/ñ del
     * español. Nota: algunos clones genericos usan CP437 en vez de Latin-1 para el rango
     * alto (acentos); si tras una prueba real los acentos salen mal, es cuestion de
     * cambiar esta codificacion, no de rehacer el resto del ticket.
     */
    private void text(ByteArrayOutputStream buf, String s) {
        buf.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Convierte los bytes acumulados a una cadena hexadecimal (dos caracteres ASCII por byte). */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Linea separadora del ancho completo del papel. */
    private String line() {
        return "-".repeat(WIDTH) + "\n";
    }

    /** Acomoda dos textos en una sola linea, uno pegado a la izquierda y otro a la derecha. */
    private String padBetween(String left, String right) {
        int spaces = WIDTH - left.length() - right.length();
        if (spaces < 1) return left + " " + right; // no cupieron en una sola linea, no truena
        return left + " ".repeat(spaces) + right;
    }

    /** Recorta un texto que no quepa en el ancho del papel, para no desalinear el ticket. */
    private String wrap(String text) {
        if (text == null) return "";
        return text.length() > WIDTH ? text.substring(0, WIDTH) : text;
    }

    private String money(BigDecimal amount) {
        return MONEY_FMT.format(amount != null ? amount : BigDecimal.ZERO);
    }

    /** "1.000" -> "1", pero conserva decimales reales como "1.500" -> "1.5". */
    private String stripTrailingZeros(BigDecimal qty) {
        return qty.stripTrailingZeros().toPlainString();
    }
}
