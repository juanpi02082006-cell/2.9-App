package com.dosnueve.app.domain;

import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Todas las fórmulas de 2.9 (doc 02 · 3). Sin dependencias de Android, para probarla
 * con pruebas unitarias.
 *
 * <p>Nada de lo que calcula se guarda (RN-12): las pantallas la llaman cada vez con la
 * materia y sus notas. Para simular, basta con pasarle las notas reales más las
 * hipotéticas.
 *
 * <p>Los números salen sin redondear, salvo "cuánto necesitas", que se redondea hacia
 * arriba (doc 02 · 3.4). Cómo se muestran lo decide {@code util.Formato}.
 */
public final class CalculadoraNotas {

    /** Ids de los componentes de la forma de calificar predeterminada (doc 05 · 3.1). */
    public static final String ID_PARCIAL = "parcial";
    public static final String ID_TRABAJOS = "trabajos";
    public static final String ID_AUTOEVALUACION = "auto";
    /** Nota que se supone en la autoevaluación mientras no la tenga (RN-13). */
    public static final double AUTOEVALUACION_SUPUESTA = 4.5;
    public static final double NOTA_MAXIMA = 5.0;

    /**
     * Margen para comparar decimales: en Java 0.1 + 0.2 da 0.30000000000000004, y un 3.0
     * calculado puede salir 2.9999999999999996. Es mucho menor que cualquier diferencia
     * real entre notas, así que no hace pasar a nadie con 2.99 (RN-08).
     */
    private static final double MARGEN = 1e-9;

    private CalculadoraNotas() {
    }

    /**
     * Nota de un componente (doc 02 · 3.1): promedio simple si todas valen igual, o
     * ponderado por el peso de cada nota si es "Con porcentajes". {@code null} si no
     * tiene notas (pendiente).
     *
     * <p>Los pesos van en % del corte, como los da el profesor (15 + 10 + 5 = 30). Mientras
     * no sumen todo el componente, la nota es el promedio ponderado de lo registrado.
     *
     * <p>En "Con porcentajes", las notas sin peso no cuentan; si ninguna tiene peso
     * todavía (por ejemplo, recién se cambió de modo), se usa el promedio simple para no
     * dejar el componente sin nota.
     */
    public static Double notaComponente(int numeroCorte, Componente componente, List<Nota> notas) {
        double suma = 0;
        int cantidad = 0;
        double sumaPonderada = 0;
        double sumaPesos = 0;
        for (Nota nota : notas) {
            if (nota.corte != numeroCorte || !Objects.equals(nota.componenteId, componente.id)) {
                continue;
            }
            suma += nota.valor;
            cantidad++;
            if (nota.peso != null && nota.peso > 0) {
                sumaPonderada += nota.valor * nota.peso;
                sumaPesos += nota.peso;
            }
        }
        if (cantidad == 0) {
            return null;
        }
        if (!componente.promedioIgual && sumaPesos > 0) {
            return sumaPonderada / sumaPesos;
        }
        return suma / cantidad;
    }

    /**
     * Cuánto del corte ya repartieron las notas de un componente "Con porcentajes": la
     * suma de sus pesos, en % del corte (un quiz de 15 % y un taller de 10 % dan 25). Las
     * notas sin peso no cuentan.
     */
    public static double pesoRegistrado(int numeroCorte, Componente componente, List<Nota> notas) {
        double suma = 0;
        for (Nota nota : notas) {
            if (nota.corte == numeroCorte && Objects.equals(nota.componenteId, componente.id)
                    && nota.peso != null && nota.peso > 0) {
                suma += nota.peso;
            }
        }
        return suma;
    }

    /**
     * Lo que le queda por repartir al componente: su % del corte menos lo ya registrado
     * (RN-04). Negativo si los pesos se pasan; la pantalla lo marca en rojo.
     */
    public static double pesoDisponible(int numeroCorte, Componente componente, List<Nota> notas) {
        return componente.porcentaje - pesoRegistrado(numeroCorte, componente, notas);
    }

    /** Los pesos de las notas se pasan del % del componente (RN-04). */
    public static boolean pesosSePasan(int numeroCorte, Componente componente, List<Nota> notas) {
        return pesoDisponible(numeroCorte, componente, notas) < -MARGEN;
    }

    /**
     * Cuánto del corte le falta a un componente por tener notas: todo su % si no tiene
     * ninguna; en "Con porcentajes", lo que sus notas todavía no cubren (de 30 % con 20 %
     * registrado faltan 10). No se supone qué va a sacar ahí.
     */
    public static double porcentajeSinNotas(int numeroCorte, Componente componente, List<Nota> notas) {
        if (notaComponente(numeroCorte, componente, notas) == null) {
            return componente.porcentaje;
        }
        if (usaPesos(numeroCorte, componente, notas)) {
            return Math.max(0, pesoDisponible(numeroCorte, componente, notas));
        }
        return 0;
    }

    /** Puntos del corte que ya aporta un componente con lo que tiene registrado. */
    private static double aporteConocido(int numeroCorte, Componente componente, List<Nota> notas) {
        Double nota = notaComponente(numeroCorte, componente, notas);
        if (nota == null) {
            return 0;
        }
        if (usaPesos(numeroCorte, componente, notas)) {
            double cubierto = Math.min(pesoRegistrado(numeroCorte, componente, notas), componente.porcentaje);
            return nota * cubierto / 100;
        }
        return nota * componente.porcentaje / 100;
    }

    private static boolean usaPesos(int numeroCorte, Componente componente, List<Nota> notas) {
        return !componente.promedioIgual && pesoRegistrado(numeroCorte, componente, notas) > 0;
    }

    /**
     * Un corte está evaluado cuando a ningún componente le falta nada (RN-06): todos
     * tienen al menos una nota y, en "Con porcentajes", sus notas cubren todo su %.
     * Un corte sin componentes no cuenta como evaluado.
     */
    public static boolean corteEvaluado(Corte corte, List<Nota> notas) {
        if (corte.componentes.isEmpty()) {
            return false;
        }
        for (Componente componente : corte.componentes) {
            if (porcentajeSinNotas(corte.numero, componente, notas) > MARGEN) {
                return false;
            }
        }
        return true;
    }

    /** Nota de un corte (doc 02 · 3.2). {@code null} si todavía no está evaluado. */
    public static Double notaCorte(Corte corte, List<Nota> notas) {
        if (!corteEvaluado(corte, notas)) {
            return null;
        }
        double nota = 0;
        for (Componente componente : corte.componentes) {
            nota += notaComponente(corte.numero, componente, notas) * componente.porcentaje / 100;
        }
        return nota;
    }

    /**
     * Lo que lleva la materia, cuánto necesita en lo que falta y la definitiva
     * (doc 02 · 3.3, 3.4 y 3.6).
     */
    public static Resumen resumen(Materia materia, List<Nota> notas) {
        double puntosAcumulados = 0;
        double porcentajeEvaluado = 0;
        boolean todosEvaluados = !materia.cortes.isEmpty();
        for (Corte corte : materia.cortes) {
            Double nota = notaCorte(corte, notas);
            if (nota == null) {
                todosEvaluados = false;
            } else {
                puntosAcumulados += nota * corte.porcentaje / 100;
                porcentajeEvaluado += corte.porcentaje;
            }
        }

        Double llevas = porcentajeEvaluado > MARGEN
                ? puntosAcumulados / (porcentajeEvaluado / 100)
                : null;
        Double definitiva = todosEvaluados ? puntosAcumulados : null;

        Double necesitas = null;
        double porcentajeRestante = 100 - porcentajeEvaluado;
        if (!todosEvaluados && porcentajeRestante > MARGEN) {
            necesitas = redondearArriba(
                    (materia.notaParaPasar - puntosAcumulados) / (porcentajeRestante / 100));
        }

        boolean enRiesgo = llevas != null && llevas < materia.notaParaPasar - MARGEN;
        return new Resumen(puntosAcumulados, porcentajeEvaluado, llevas, necesitas, definitiva,
                enRiesgo, materia.notaParaPasar);
    }

    /**
     * Cuánto necesita en lo que falta de un corte en curso para llegar a su meta
     * (doc 02 · 3.5). La meta es el "cuánto necesitas" de la materia.
     *
     * <p>Si la autoevaluación no tiene nota se supone 4.5 (RN-13) y el resultado lo dice,
     * para que la pantalla lo muestre. Si lo único que falta es la autoevaluación, se
     * calcula la nota que necesita ahí, sin suponer nada. Si faltan varias cosas además
     * de la autoevaluación, es la nota que necesita en cada una.
     *
     * <p>En "Con porcentajes", el % de trabajos que todavía no tiene notas también está
     * pendiente: no se supone que ahí saque lo mismo que lleva. Con 20 de 30 % registrado,
     * la nota necesaria vale para el parcial y para el 10 % que falta.
     *
     * @return {@code null} si al corte no le falta nada.
     */
    public static NecesarioEnCorte necesarioEnCorte(Corte corte, List<Nota> notas, double metaCorte) {
        int pendientes = 0;
        boolean faltaAutoevaluacion = false;
        for (Componente componente : corte.componentes) {
            if (porcentajeSinNotas(corte.numero, componente, notas) > MARGEN) {
                pendientes++;
                if (esAutoevaluacion(componente)) {
                    faltaAutoevaluacion = true;
                }
            }
        }
        if (pendientes == 0) {
            return null;
        }
        boolean suponerAutoevaluacion = faltaAutoevaluacion && pendientes > 1;

        double aportesConocidos = 0;
        double porcentajePendiente = 0;
        List<Pendiente> enQue = new ArrayList<>();
        for (Componente componente : corte.componentes) {
            aportesConocidos += aporteConocido(corte.numero, componente, notas);
            double sinNotas = porcentajeSinNotas(corte.numero, componente, notas);
            if (sinNotas <= MARGEN) {
                continue;
            }
            if (suponerAutoevaluacion && esAutoevaluacion(componente)) {
                aportesConocidos += AUTOEVALUACION_SUPUESTA * sinNotas / 100;
            } else {
                porcentajePendiente += sinNotas;
                enQue.add(new Pendiente(componente.id, sinNotas,
                        sinNotas < componente.porcentaje - MARGEN));
            }
        }
        if (porcentajePendiente <= MARGEN) {
            return null;
        }
        double nota = redondearArriba((metaCorte - aportesConocidos) / (porcentajePendiente / 100));
        return new NecesarioEnCorte(nota, suponerAutoevaluacion, enQue);
    }

    /**
     * El corte en curso: el primero que todavía no está evaluado (RF-13). {@code null} si
     * la materia ya tiene todos sus cortes.
     */
    public static Corte corteEnCurso(Materia materia, List<Nota> notas) {
        for (Corte corte : materia.cortes) {
            if (!corteEvaluado(corte, notas)) {
                return corte;
            }
        }
        return null;
    }

    /**
     * El parcial y la autoevaluación son una sola nota por corte. Los demás tipos (Trabajos
     * y quices, y los que agregue el estudiante) admiten varias y pueden ir "Con
     * porcentajes" (RN-04).
     */
    public static boolean esUnaSolaNota(String componenteId) {
        return ID_PARCIAL.equals(componenteId) || ID_AUTOEVALUACION.equals(componenteId);
    }

    /**
     * La nota de una materia para los promedios: la final si es de un semestre anterior
     * (RN-09) o lo que lleva (que es la definitiva cuando ya tiene todos los cortes).
     * {@code null} si todavía no tiene nota.
     */
    public static Double notaParaPromedio(Materia materia, List<Nota> notas) {
        if (materia.notaFinal != null) {
            return materia.notaFinal;
        }
        return resumen(materia, notas).llevas;
    }

    /**
     * Promedio ponderado por créditos (doc 02 · 3.7, RN-10). Las materias sin nota no
     * cuentan. {@code null} si ninguna tiene nota. Para el promedio acumulado, pasar
     * solo las materias de semestres terminados (RN-11).
     */
    public static Double promedioPonderado(List<NotaConCreditos> materias) {
        double puntos = 0;
        int creditos = 0;
        for (NotaConCreditos materia : materias) {
            if (materia.nota != null && materia.creditos > 0) {
                puntos += materia.nota * materia.creditos;
                creditos += materia.creditos;
            }
        }
        return creditos == 0 ? null : puntos / creditos;
    }

    /** Se aprueba con la nota para pasar o más, sin redondear: 2.99 no pasa (RN-08). */
    public static boolean aprueba(double nota, double notaParaPasar) {
        return nota >= notaParaPasar - MARGEN;
    }

    /**
     * Redondea hacia arriba a un decimal (3.194 → 3.2), para que con esa nota de verdad
     * se pase (doc 02 · 3.4). Primero quita el error de los decimales de Java, para que
     * un 2.7 calculado como 2.7000000000000002 no suba a 2.8.
     */
    public static double redondearArriba(double valor) {
        return BigDecimal.valueOf(valor)
                .setScale(6, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.CEILING)
                .doubleValue();
    }

    private static boolean esAutoevaluacion(Componente componente) {
        return ID_AUTOEVALUACION.equals(componente.id);
    }

    /** Resultado de {@link #resumen}. */
    public static final class Resumen {

        /** Σ (nota del corte × % del corte) / 100, solo de los cortes evaluados. */
        public final double puntosAcumulados;
        /** Suma de los porcentajes de los cortes evaluados. */
        public final double porcentajeEvaluado;
        /** Lo que lleva. {@code null} si no tiene ningún corte evaluado. */
        public final Double llevas;
        /**
         * Lo que necesita en cada corte que falta, ya redondeado hacia arriba. También es
         * la meta de esos cortes. {@code null} si ya tiene todos los cortes.
         */
        public final Double necesitas;
        /** {@code null} mientras falte algún corte. */
        public final Double definitiva;
        /** Lo que lleva es menor que la nota para pasar (RN-07). */
        public final boolean enRiesgo;
        private final double notaParaPasar;

        Resumen(double puntosAcumulados, double porcentajeEvaluado, Double llevas,
                Double necesitas, Double definitiva, boolean enRiesgo, double notaParaPasar) {
            this.puntosAcumulados = puntosAcumulados;
            this.porcentajeEvaluado = porcentajeEvaluado;
            this.llevas = llevas;
            this.necesitas = necesitas;
            this.definitiva = definitiva;
            this.enRiesgo = enRiesgo;
            this.notaParaPasar = notaParaPasar;
        }

        /** "Ya no alcanza": necesitaría más de 5.0. */
        public boolean yaNoAlcanza() {
            return necesitas != null && necesitas > NOTA_MAXIMA;
        }

        /** "¡Ya pasaste!": aunque saque 0 en lo que falta, pasa. */
        public boolean yaPaso() {
            return necesitas != null && necesitas <= 0;
        }

        /** Aprobó la materia (RN-08). Solo cuando ya tiene la definitiva. */
        public boolean aprobada() {
            return definitiva != null && aprueba(definitiva, notaParaPasar);
        }
    }

    /** Resultado de {@link #necesarioEnCorte}. */
    public static final class NecesarioEnCorte {

        /** Nota necesaria, ya redondeada hacia arriba. Puede ser más de 5.0 o 0 o menos. */
        public final double nota;
        /** Se supuso 4.5 en la autoevaluación: la pantalla lo tiene que decir (RN-13). */
        public final boolean autoevaluacionSupuesta;
        /** En qué se necesita esa nota, en el orden del corte: "el parcial", "el 10 % de trabajos"… */
        public final List<Pendiente> enQue;

        NecesarioEnCorte(double nota, boolean autoevaluacionSupuesta, List<Pendiente> enQue) {
            this.nota = nota;
            this.autoevaluacionSupuesta = autoevaluacionSupuesta;
            this.enQue = enQue;
        }
    }

    /** Algo del corte que todavía no tiene nota. */
    public static final class Pendiente {

        public final String componenteId;
        /** % del corte que falta. */
        public final double porcentaje;
        /** Solo falta una parte (Con porcentajes con algunas notas ya registradas). */
        public final boolean parcial;

        Pendiente(String componenteId, double porcentaje, boolean parcial) {
            this.componenteId = componenteId;
            this.porcentaje = porcentaje;
            this.parcial = parcial;
        }
    }

    /** Una materia para {@link #promedioPonderado}: su nota y sus créditos. */
    public static final class NotaConCreditos {

        public final Double nota;
        public final int creditos;

        public NotaConCreditos(Double nota, int creditos) {
            this.nota = nota;
            this.creditos = creditos;
        }
    }
}
