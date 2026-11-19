package com.dosnueve.app.domain;

import java.io.Serializable;

/**
 * Un "momento": lo que se celebra (o se acompaña) cuando se cierra un corte o el semestre.
 * Aquí solo se decide qué tipo de momento es y con qué números; los mensajes y la
 * animación los pone {@code ui.momento.MomentoDialog}.
 *
 * <p>Sin Android, para probarlo con pruebas unitarias.
 */
public final class Momento implements Serializable {

    public enum Tipo {
        /** 4.5 o más. */
        DE_LUJO,
        /** De 4.0 a 4.4. */
        MUY_BIEN,
        /** Pasó con medio punto o más de margen. */
        BIEN,
        /** Pasó por menos de medio punto. */
        RASPANDO,
        /** No pasó el corte, pero la materia todavía se puede. */
        NO_PASO,
        /** No pasó el corte, pero con él la materia quedó aprobada. */
        LA_SACASTE,
        /** La materia ya no alcanza (o quedó perdida). */
        YA_NO_ALCANZA,
        SEMESTRE_EXCELENTE,
        SEMESTRE_TODO_ADENTRO,
        SEMESTRE_CON_PERDIDAS;

        public boolean esSemestre() {
            return this == SEMESTRE_EXCELENTE || this == SEMESTRE_TODO_ADENTRO
                    || this == SEMESTRE_CON_PERDIDAS;
        }

        /** Los que se celebran con confeti. */
        public boolean celebra() {
            return this != NO_PASO && this != YA_NO_ALCANZA;
        }
    }

    private static final double MARGEN = 1e-9;
    /** Por debajo de nota para pasar + esto, "raspando". */
    private static final double MEDIO_PUNTO = 0.5;

    public final Tipo tipo;

    // Del corte
    public final String materia;
    public final int corte;
    /** La nota del corte, o el promedio del semestre. */
    public final double nota;
    public final Double llevas;
    public final Double necesitas;
    public final Double definitiva;

    // Del semestre
    public final String periodo;
    public final int aprobadas;
    public final int total;
    public final String mejorMateria;
    public final double mejorNota;
    public final int semanas;

    private Momento(Tipo tipo, String materia, int corte, double nota, Double llevas,
                    Double necesitas, Double definitiva, String periodo, int aprobadas, int total,
                    String mejorMateria, double mejorNota, int semanas) {
        this.tipo = tipo;
        this.materia = materia;
        this.corte = corte;
        this.nota = nota;
        this.llevas = llevas;
        this.necesitas = necesitas;
        this.definitiva = definitiva;
        this.periodo = periodo;
        this.aprobadas = aprobadas;
        this.total = total;
        this.mejorMateria = mejorMateria;
        this.mejorNota = mejorNota;
        this.semanas = semanas;
    }

    /**
     * Se cerró un corte.
     *
     * @param resumen el de la materia, ya con este corte adentro
     */
    public static Momento deCorte(String materia, int corte, double nota, double paraPasar,
                                  CalculadoraNotas.Resumen resumen) {
        return new Momento(tipoDeCorte(nota, paraPasar, resumen), materia, corte, nota,
                resumen.llevas, resumen.necesitas, resumen.definitiva, "", 0, 0, "", 0, 0);
    }

    static Tipo tipoDeCorte(double nota, double paraPasar, CalculadoraNotas.Resumen resumen) {
        boolean materiaCerrada = resumen.definitiva != null;
        boolean materiaAprobada = materiaCerrada && CalculadoraNotas.aprueba(resumen.definitiva, paraPasar);

        if (CalculadoraNotas.aprueba(nota, paraPasar)) {
            // Pasar el último corte no se celebra si la materia igual quedó perdida
            if (materiaCerrada && !materiaAprobada) {
                return Tipo.YA_NO_ALCANZA;
            }
            if (nota >= 4.5 - MARGEN) {
                return Tipo.DE_LUJO;
            }
            if (nota >= 4.0 - MARGEN) {
                return Tipo.MUY_BIEN;
            }
            return nota >= paraPasar + MEDIO_PUNTO - MARGEN ? Tipo.BIEN : Tipo.RASPANDO;
        }

        if (materiaCerrada) {
            return materiaAprobada ? Tipo.LA_SACASTE : Tipo.YA_NO_ALCANZA;
        }
        boolean todaviaSePuede = resumen.necesitas != null
                && resumen.necesitas <= CalculadoraNotas.NOTA_MAXIMA + MARGEN;
        return todaviaSePuede ? Tipo.NO_PASO : Tipo.YA_NO_ALCANZA;
    }

    /** Todas las materias quedaron con definitiva. */
    public static Momento deSemestre(String periodo, double promedio, int aprobadas, int total,
                                     String mejorMateria, double mejorNota, int semanas) {
        Tipo tipo;
        if (aprobadas < total) {
            tipo = Tipo.SEMESTRE_CON_PERDIDAS;
        } else if (promedio >= 4.0 - MARGEN) {
            tipo = Tipo.SEMESTRE_EXCELENTE;
        } else {
            tipo = Tipo.SEMESTRE_TODO_ADENTRO;
        }
        return new Momento(tipo, "", 0, promedio, null, null, null, periodo, aprobadas, total,
                mejorMateria, mejorNota, semanas);
    }
}
