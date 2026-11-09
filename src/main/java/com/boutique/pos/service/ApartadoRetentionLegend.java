package com.boutique.pos.service;

import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.Sale;
import com.boutique.pos.repository.ApartadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Leyenda del ticket de un anticipo o abono. Con % de retención ya no se imprime nada (se
 * quitó la leyenda "Si cancela o devuelve por cambio de opinión..."); solo los apartados sin
 * retención dicen "Anticipo no reembolsable". La usan el ticket impreso y el PDF.
 */
@Component
@RequiredArgsConstructor
public class ApartadoRetentionLegend {

    private final ApartadoRepository apartadoRepository;

    /** La leyenda, o null si no lleva ninguna. */
    public String text(Sale sale) {
        Apartado apartado = sale.getApartadoId() == null ? null : apartadoRepository.findById(sale.getApartadoId()).orElse(null);
        return apartado != null && apartado.getRetentionPercent() != null ? null : "Anticipo no reembolsable";
    }
}
