package com.dosnueve.app.data.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Un corte de la materia, con su porcentaje de la nota final y sus componentes
 * (doc 05 · 3, lista {@code cortes} de la materia).
 */
public class Corte {

    /** 1, 2, 3… Las notas dicen a qué corte pertenecen con este número. */
    public int numero;
    /** Porcentaje de la nota final. Los de una materia suman 100 (RN-02). */
    public double porcentaje;
    public List<Componente> componentes = new ArrayList<>();

    public Corte() {
    }

    public Corte(int numero, double porcentaje, List<Componente> componentes) {
        this.numero = numero;
        this.porcentaje = porcentaje;
        this.componentes = componentes;
    }
}
