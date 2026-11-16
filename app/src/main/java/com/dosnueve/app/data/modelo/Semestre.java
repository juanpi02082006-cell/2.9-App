package com.dosnueve.app.data.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Un semestre ya terminado (doc 05 · 3, {@code …/semestres/{semestreId}}). Sus materias
 * solo guardan la nota final y los créditos, sin cortes (RN-09).
 *
 * <p>El semestre en curso no se guarda aquí: son las materias del Inicio.
 */
public class Semestre {

    public String id;
    /** "2025-1", "2025-2"… */
    public String periodo;
    public List<Materia> materias = new ArrayList<>();

    public Semestre() {
    }
}
