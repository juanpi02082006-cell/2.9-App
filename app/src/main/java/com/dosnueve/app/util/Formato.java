package com.dosnueve.app.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Cómo se muestran los números de 2.9 (doc 02 · 3.8).
 *
 * <p>Se usa punto decimal a propósito, aunque el idioma de la app sea español de
 * Colombia: la marca es "2.9" y todos los ejemplos de la documentación van con punto.
 */
public final class Formato {

    /** Lo que se muestra cuando algo todavía no tiene nota. */
    public static final String PENDIENTE = "—";

    private Formato() {
    }

    /** Notas y promedios: un decimal (2.9, 3.7). */
    public static String nota(Double valor) {
        if (valor == null) {
            return PENDIENTE;
        }
        return redondear(valor, 1).toPlainString();
    }

    /**
     * Igual que {@link #nota}, pero si el valor queda justo por debajo de la nota para
     * pasar y al redondear daría exactamente esa nota, se muestra con dos decimales
     * para no dar falsas esperanzas (2.968 → "2.97", no "3.0").
     */
    public static String notaSinFalsasEsperanzas(Double valor, double notaParaPasar) {
        if (valor == null) {
            return PENDIENTE;
        }
        boolean noPasa = valor < notaParaPasar;
        boolean alRedondearPareceQuePasa = redondear(valor, 1).doubleValue() >= notaParaPasar;
        if (noPasa && alRedondearPareceQuePasa) {
            return redondear(valor, 2).toPlainString();
        }
        return nota(valor);
    }

    /**
     * "Cuánto necesitas" se redondea hacia arriba a un decimal (3.194 → 3.2), para que
     * con esa nota de verdad se pase (doc 02 · 3.4).
     */
    public static double redondearArriba(double valor) {
        return BigDecimal.valueOf(valor)
                .setScale(1, RoundingMode.CEILING)
                .doubleValue();
    }

    /** Porcentajes: sin decimales si es entero (33 %), con uno si no (33.5 %). */
    public static String porcentaje(double valor) {
        BigDecimal redondeado = redondear(valor, 1);
        if (redondeado.stripTrailingZeros().scale() <= 0) {
            return String.valueOf(redondeado.intValue());
        }
        return redondeado.toPlainString();
    }

    /** Fecha guardada como {@code yyyy-MM-dd}, como se lee: "12 sep 2026". Vacía si no hay. */
    public static String fecha(String iso) {
        if (iso == null || iso.isEmpty()) {
            return "";
        }
        try {
            return LocalDate.parse(iso).format(COMO_SE_LEE);
        } catch (DateTimeParseException malEscrita) {
            return iso;
        }
    }

    private static final DateTimeFormatter COMO_SE_LEE =
            DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "CO"));

    private static BigDecimal redondear(double valor, int decimales) {
        return BigDecimal.valueOf(valor).setScale(decimales, RoundingMode.HALF_UP);
    }
}
