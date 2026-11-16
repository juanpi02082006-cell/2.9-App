package com.dosnueve.app.data.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Una materia del semestre (doc 05 · 3, {@code …/materias/{materiaId}}). Las notas no
 * van aquí: están en su propia subcolección (ver {@link Nota}).
 *
 * <p>Faltan {@code creadaEn} y la anotación {@code @DocumentId} del id: llegan con
 * Firebase, en la Fase 3.
 */
public class Materia {

    public String id;
    public String nombre;
    /** Opcional. */
    public String profesor;
    /** De 1 a 10. Pesan en los promedios (RN-10). */
    public int creditos;
    public double notaParaPasar = 3.0;
    /** Solo en semestres anteriores; entonces {@link #cortes} va vacío (RN-09). */
    public Double notaFinal;
    /** Copia de la forma de calificar al crear la materia (RN-05). */
    public List<Corte> cortes = new ArrayList<>();

    public Materia() {
    }
}
