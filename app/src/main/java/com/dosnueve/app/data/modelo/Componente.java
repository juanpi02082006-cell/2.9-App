package com.dosnueve.app.data.modelo;

/**
 * Un tipo de nota dentro de un corte: Parcial, Trabajos y quices, Autoevaluación…
 * (doc 05 · 3, lista {@code componentes} de cada corte de la materia).
 *
 * <p>Clase simple, con constructor vacío y atributos públicos, como la pide Firestore.
 */
public class Componente {

    /** Id dentro del corte: {@code parcial}, {@code trabajos}, {@code auto}… */
    public String id;
    public String nombre;
    /** Porcentaje dentro del corte. Los de un corte suman 100 (RN-03). */
    public double porcentaje;
    /**
     * {@code true}: todas las notas valen igual. {@code false}: cada nota trae su peso
     * ("Con porcentajes", RN-04).
     */
    public boolean promedioIgual = true;

    public Componente() {
    }

    public Componente(String id, String nombre, double porcentaje, boolean promedioIgual) {
        this.id = id;
        this.nombre = nombre;
        this.porcentaje = porcentaje;
        this.promedioIgual = promedioIgual;
    }
}
