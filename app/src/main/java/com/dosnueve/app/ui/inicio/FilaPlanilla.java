package com.dosnueve.app.ui.inicio;

/**
 * Una fila de la planilla del Inicio (pantalla 03).
 *
 * <p>Solo lo que se pinta: los números ya vienen calculados por CalculadoraNotas,
 * porque lo que se calcula no se guarda (RN-12).
 */
public class FilaPlanilla {

    public final long materiaId;
    public final String nombre;
    public final int creditos;
    /** Nota de cada corte, en orden. {@code null} si el corte aún no está evaluado. */
    public final Double[] notasCortes;
    /** Lo que lleva la materia. {@code null} si no tiene ningún corte evaluado. */
    public final Double llevas;
    /** En rojo si lo que lleva es menor que su nota para pasar (RN-07). */
    public final boolean enRiesgo;
    public final double notaParaPasar;

    public FilaPlanilla(long materiaId, String nombre, int creditos, Double[] notasCortes,
                        Double llevas, boolean enRiesgo, double notaParaPasar) {
        this.materiaId = materiaId;
        this.nombre = nombre;
        this.creditos = creditos;
        this.notasCortes = notasCortes;
        this.llevas = llevas;
        this.enRiesgo = enRiesgo;
        this.notaParaPasar = notaParaPasar;
    }
}
