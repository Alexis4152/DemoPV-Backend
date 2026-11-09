package com.boutique.pos.service;

import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.Sale;
import com.boutique.pos.repository.ApartadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Leyenda del ticket de un anticipo o abono: qué se retiene si el cliente cancela (ver
 * {@code ApartadoService#retention}). Tiene que estar en el ticket desde el primer pago para
 * que la retención sea válida. La usan el ticket impreso y el PDF.
 */
@Component
@RequiredArgsConstructor
public class ApartadoRetentionLegend {

    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private final ApartadoRepository apartadoRepository;

    public String text(Sale sale) {
        Apartado apartado = sale.getApartadoId() == null ? null : apartadoRepository.findById(sale.getApartadoId()).orElse(null);
        BigDecimal percent = apartado != null ? apartado.getRetentionPercent() : null;
        if (percent == null) return "Anticipo no reembolsable";
        BigDecimal amount = apartado.getTotal().multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return "Si cancela o devuelve por cambio de opinión, la tienda retiene el "
                + percent.stripTrailingZeros().toPlainString() + "% del total del apartado ("
                + MONEY_FMT.format(amount) + "). Producto defectuoso: se devuelve todo.";
    }
}
