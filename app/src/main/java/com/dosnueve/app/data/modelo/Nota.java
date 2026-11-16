package com.dosnueve.app.data.modelo;

/**
 * Una nota (doc 05 · 3, {@code …/materias/{materiaId}/notas/{notaId}}).
 *
 * <p>Falta la anotación {@code @DocumentId} del id: llega con Firebase, en la Fase 3.
 */
public class Nota {

    public String id;
    /** Número del corte (1, 2, 3…). */
    public int corte;
    /** Id del componente dentro del corte: {@code parcial}, {@code trabajos}, {@code auto}… */
    public String componenteId;
    /** Opcional (Quiz 1, Taller 2). */
    public String nombre;
    /** De 0.0 a 5.0 (RN-01). */
    public double valor;
    /**
     * Cuánto vale, en % del corte (un quiz de 15 % → 15). Solo si el componente es "Con
     * porcentajes" ({@code promedioIgual = false}); no pasan del % del componente (RN-04).
     */
    public Double peso;
    /** {@code yyyy-MM-dd} */
    public String fecha;

    public Nota() {
    }

    public Nota(int corte, String componenteId, double valor) {
        this.corte = corte;
        this.componenteId = componenteId;
        this.valor = valor;
    }

    public Nota(int corte, String componenteId, double valor, Double peso) {
        this(corte, componenteId, valor);
        this.peso = peso;
    }
}
