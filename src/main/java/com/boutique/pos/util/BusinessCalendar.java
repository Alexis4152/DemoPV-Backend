package com.boutique.pos.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Cálculo de días hábiles para los plazos legales de ARCO ({@code ArcoRequestService}).
 * <p>
 * <b>Simplificación a propósito:</b> solo excluye sábados y domingos — no lleva un
 * calendario de días festivos oficiales de México. Para una boutique pequeña, un par de
 * días de margen de más en la práctica (festivo contado como hábil) es un riesgo menor
 * comparado con la complejidad de mantener un calendario de festivos actualizado cada año;
 * si el negocio crece y esto importa más, lo primero a agregar sería una tabla de festivos.
 */
public final class BusinessCalendar {

    private BusinessCalendar() {}

    /** Suma {@code days} días HÁBILES (sin sábados/domingos) a {@code start}. */
    public static LocalDate addBusinessDays(LocalDate start, int days) {
        LocalDate date = start;
        int added = 0;
        while (added < days) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }
}
