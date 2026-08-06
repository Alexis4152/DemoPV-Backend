package com.boutique.pos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Convierte un monto a su leyenda en letras para el ticket, ej. 82.00 -> "OCHENTA Y DOS PESOS 00/100 M.N."
public final class SpanishNumberToWords {

    private SpanishNumberToWords() {}

    private static final String[] UNIDADES = {
            "", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte"
    };
    private static final String[] DECENAS = {
            "", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"
    };
    private static final String[] CENTENAS = {
            "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"
    };

    public static String pesos(BigDecimal amount) {
        BigDecimal abs = (amount != null ? amount : BigDecimal.ZERO).abs().setScale(2, RoundingMode.HALF_UP);
        long enteros = abs.longValue();
        int centavos = abs.subtract(BigDecimal.valueOf(enteros)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();

        String letras = convert(enteros);
        letras = applyApocope(letras);
        letras = letras.substring(0, 1).toUpperCase() + letras.substring(1);

        return letras + " pesos " + String.format("%02d", centavos) + "/100 M.N.";
    }

    // "uno" -> "un" y "veintiuno" -> "veintiún" antes de "pesos" (ej. "treinta y un pesos", "veintiún pesos")
    private static String applyApocope(String words) {
        if (words.equals("uno")) return "un";
        if (words.endsWith(" uno")) return words.substring(0, words.length() - 3) + "un";
        return words.replace("veintiuno", "veintiún");
    }

    private static String convert(long n) {
        if (n == 0) return "cero";
        if (n < 0) return "menos " + convert(-n);
        if (n <= 20) return UNIDADES[(int) n];
        if (n < 30) return "veinti" + convert(n - 20);
        if (n < 100) {
            long d = n / 10, u = n % 10;
            return u == 0 ? DECENAS[(int) d] : DECENAS[(int) d] + " y " + convert(u);
        }
        if (n == 100) return "cien";
        if (n < 1000) {
            long c = n / 100, r = n % 100;
            return CENTENAS[(int) c] + (r == 0 ? "" : " " + convert(r));
        }
        if (n < 2000) {
            long r = n % 1000;
            return "mil" + (r == 0 ? "" : " " + convert(r));
        }
        if (n < 1_000_000) {
            long m = n / 1000, r = n % 1000;
            return convert(m) + " mil" + (r == 0 ? "" : " " + convert(r));
        }
        if (n < 2_000_000) {
            long r = n % 1_000_000;
            return "un millón" + (r == 0 ? "" : " " + convert(r));
        }
        long m = n / 1_000_000, r = n % 1_000_000;
        return convert(m) + " millones" + (r == 0 ? "" : " " + convert(r));
    }
}
