package com.dosnueve.app.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Qué momento sale al cerrar un corte o el semestre (nota para pasar 3.0). */
public class MomentoTest {

    /** Materia a medio camino: lleva algo y le falta. */
    private static CalculadoraNotas.Resumen enCurso(double necesitas) {
        return new CalculadoraNotas.Resumen(1.0, 33, 3.0, necesitas, null, false, 3.0);
    }

    /** Materia con todos los cortes. */
    private static CalculadoraNotas.Resumen cerrada(double definitiva) {
        return new CalculadoraNotas.Resumen(definitiva, 100, definitiva, null, definitiva, false, 3.0);
    }

    private static Momento.Tipo tipo(double nota, CalculadoraNotas.Resumen resumen) {
        return Momento.tipoDeCorte(nota, 3.0, resumen);
    }

    @Test
    public void losQuePasan() {
        assertEquals(Momento.Tipo.DE_LUJO, tipo(4.5, enCurso(2.8)));
        assertEquals(Momento.Tipo.MUY_BIEN, tipo(4.0, enCurso(2.8)));
        assertEquals(Momento.Tipo.BIEN, tipo(3.5, enCurso(2.8)));
        assertEquals(Momento.Tipo.RASPANDO, tipo(3.0, enCurso(2.8)));
        assertEquals(Momento.Tipo.RASPANDO, tipo(3.4, enCurso(2.8)));
    }

    @Test
    public void elQueNoPasaPeroTodaviaPuede() {
        assertEquals(Momento.Tipo.NO_PASO, tipo(2.4, enCurso(3.4)));
        assertEquals(Momento.Tipo.NO_PASO, tipo(2.99, enCurso(5.0)));
    }

    @Test
    public void cuandoYaNoAlcanza() {
        assertEquals(Momento.Tipo.YA_NO_ALCANZA, tipo(1.0, enCurso(5.2)));
        assertEquals(Momento.Tipo.YA_NO_ALCANZA, tipo(2.0, cerrada(2.6)));
        // Pasó el último corte, pero la materia igual quedó perdida
        assertEquals(Momento.Tipo.YA_NO_ALCANZA, tipo(3.2, cerrada(2.8)));
    }

    @Test
    public void perdioElCortePeroSacoLaMateria() {
        assertEquals(Momento.Tipo.LA_SACASTE, tipo(2.5, cerrada(3.3)));
    }

    @Test
    public void elSemestre() {
        assertEquals(Momento.Tipo.SEMESTRE_EXCELENTE,
                Momento.deSemestre("2026-2", 4.1, 6, 6, "Física", 4.6, 16).tipo);
        assertEquals(Momento.Tipo.SEMESTRE_TODO_ADENTRO,
                Momento.deSemestre("2026-2", 3.6, 6, 6, "Física", 4.0, 16).tipo);
        assertEquals(Momento.Tipo.SEMESTRE_CON_PERDIDAS,
                Momento.deSemestre("2026-2", 4.2, 5, 6, "Física", 4.8, 16).tipo);
    }
}
