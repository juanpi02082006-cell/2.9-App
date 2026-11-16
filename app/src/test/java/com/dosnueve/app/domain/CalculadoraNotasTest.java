package com.dosnueve.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas.NecesarioEnCorte;
import com.dosnueve.app.domain.CalculadoraNotas.NotaConCreditos;
import com.dosnueve.app.domain.CalculadoraNotas.Resumen;
import com.dosnueve.app.util.Formato;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Los casos del ejemplo Cálculo II (doc 02 · 4), con los mismos resultados que el
 * documento, más algunos bordes. Lo que se ve en pantalla se revisa con {@link Formato}.
 */
public class CalculadoraNotasTest {

    private static final double DELTA = 1e-9;

    // ---------- Corte en curso y tipos de una sola nota ----------

    @Test
    public void elCorteEnCursoEsElPrimeroSinEvaluar() {
        Materia calculo = calculoII();
        assertEquals(3, CalculadoraNotas.corteEnCurso(calculo, notasCalculoII()).numero);
    }

    @Test
    public void sinCortesPendientesNoHayCorteEnCurso() {
        Materia calculo = calculoII();
        List<Nota> notas = new ArrayList<>(notasCalculoII());
        notas.add(new Nota(3, "parcial", 3.0));
        notas.add(new Nota(3, "auto", 4.5));
        assertNull(CalculadoraNotas.corteEnCurso(calculo, notas));
    }

    @Test
    public void elParcialYLaAutoevaluacionSonUnaSolaNota() {
        assertTrue(CalculadoraNotas.esUnaSolaNota("parcial"));
        assertTrue(CalculadoraNotas.esUnaSolaNota("auto"));
        assertFalse(CalculadoraNotas.esUnaSolaNota("trabajos"));
        assertFalse(CalculadoraNotas.esUnaSolaNota("laboratorio"));
    }

    // ---------- Cálculo II, tal como está en el documento ----------

    @Test
    public void notaDeUnComponenteEsElPromedioDeSusNotas() {
        Materia calculo = calculoII();
        Componente trabajos = calculo.cortes.get(0).componentes.get(1);
        assertEquals(3.5, CalculadoraNotas.notaComponente(1, trabajos, notasCalculoII()), DELTA);
    }

    @Test
    public void notaDeLosCortesEvaluados() {
        Materia calculo = calculoII();
        List<Nota> notas = notasCalculoII();
        assertEquals(2.70, CalculadoraNotas.notaCorte(calculo.cortes.get(0), notas), DELTA);
        assertEquals(3.10, CalculadoraNotas.notaCorte(calculo.cortes.get(1), notas), DELTA);
    }

    @Test
    public void corteConComponentesPendientesNoEstaEvaluado() {
        Corte corte3 = calculoII().cortes.get(2);
        assertFalse(CalculadoraNotas.corteEvaluado(corte3, notasCalculoII()));
        assertNull(CalculadoraNotas.notaCorte(corte3, notasCalculoII()));
    }

    @Test
    public void loQueLlevaYCuantoNecesita() {
        Resumen resumen = CalculadoraNotas.resumen(calculoII(), notasCalculoII());
        assertEquals(1.914, resumen.puntosAcumulados, DELTA);
        assertEquals(66, resumen.porcentajeEvaluado, DELTA);
        assertEquals(2.9, resumen.llevas, DELTA);
        assertTrue("2.9 es menor que 3.0: en riesgo (RN-07)", resumen.enRiesgo);
        assertEquals("3.194 se redondea hacia arriba", 3.2, resumen.necesitas, DELTA);
        assertNull(resumen.definitiva);
        assertFalse(resumen.yaNoAlcanza());
        assertFalse(resumen.yaPaso());
    }

    @Test
    public void conTresPuntoUnoEnElCorteTresNoPasaYSeVeDosNoventaYSiete() {
        List<Nota> notas = notasCortesUnoYDos();
        notas.add(new Nota(3, "parcial", 3.1));
        notas.add(new Nota(3, "trabajos", 3.1));
        notas.add(new Nota(3, "auto", 3.1));

        Resumen resumen = CalculadoraNotas.resumen(calculoII(), notas);
        assertEquals(2.968, resumen.definitiva, DELTA);
        assertFalse(resumen.aprobada());
        assertEquals("2.97", Formato.notaSinFalsasEsperanzas(resumen.definitiva, 3.0));
    }

    @Test
    public void parcialNecesarioEnElCorteTres() {
        Materia calculo = calculoII();
        List<Nota> notas = notasCalculoII();
        double meta = CalculadoraNotas.resumen(calculo, notas).necesitas;

        NecesarioEnCorte parcial = CalculadoraNotas.necesarioEnCorte(calculo.cortes.get(2), notas, meta);
        assertNotNull(parcial);
        assertEquals("2.783 se redondea hacia arriba", 2.8, parcial.nota, DELTA);
        assertTrue("Supone 4.5 en autoevaluación (RN-13)", parcial.autoevaluacionSupuesta);
    }

    @Test
    public void agregarUnCuatroCincoATrabajosBajaElParcialNecesario() {
        Materia calculo = calculoII();
        List<Nota> notas = notasCalculoII();
        notas.add(new Nota(3, "trabajos", 4.5));
        Corte corte3 = calculo.cortes.get(2);

        Double trabajos = CalculadoraNotas.notaComponente(3, corte3.componentes.get(1), notas);
        assertEquals(3.825, trabajos, DELTA);
        assertEquals("3.8", Formato.nota(trabajos));

        double meta = CalculadoraNotas.resumen(calculo, notas).necesitas;
        assertEquals(2.7, CalculadoraNotas.necesarioEnCorte(corte3, notas, meta).nota, DELTA);
    }

    @Test
    public void borrarElQuizDeTresOchoBajaTrabajos() {
        Materia calculo = calculoII();
        List<Nota> notas = notasCalculoII();
        Componente trabajos = calculo.cortes.get(2).componentes.get(1);
        assertEquals(3.6, CalculadoraNotas.notaComponente(3, trabajos, notas), DELTA);

        notas.removeIf(nota -> nota.corte == 3 && nota.valor == 3.8);
        assertEquals(3.5, CalculadoraNotas.notaComponente(3, trabajos, notas), DELTA);
    }

    @Test
    public void trabajosConPorcentajes() {
        Materia calculo = calculoII();
        Corte corte3 = calculo.cortes.get(2);
        corte3.componentes.get(1).promedioIgual = false;
        List<Nota> notas = notasCortesUnoYDos();
        // Pesos en % del corte: 6 + 15 + 9 = 30 (los mismos 20/50/30 de trabajos)
        notas.add(new Nota(3, "trabajos", 3.8, 6.0));
        notas.add(new Nota(3, "trabajos", 3.4, 15.0));
        notas.add(new Nota(3, "trabajos", 3.6, 9.0));

        Double trabajos = CalculadoraNotas.notaComponente(3, corte3.componentes.get(1), notas);
        assertEquals(3.54, trabajos, DELTA);
        assertEquals("3.5", Formato.nota(trabajos));

        double meta = CalculadoraNotas.resumen(calculo, notas).necesitas;
        assertEquals(2.9, CalculadoraNotas.necesarioEnCorte(corte3, notas, meta).nota, DELTA);
    }

    @Test
    public void simularParcialTresCincoPasa() {
        Materia calculo = calculoII();
        List<Nota> notas = notasCalculoII();
        notas.add(new Nota(3, "parcial", 3.5));
        notas.add(new Nota(3, "auto", 4.5));

        assertEquals(3.63, CalculadoraNotas.notaCorte(calculo.cortes.get(2), notas), DELTA);
        Resumen resumen = CalculadoraNotas.resumen(calculo, notas);
        assertEquals("3.1", Formato.notaSinFalsasEsperanzas(resumen.definitiva, 3.0));
        assertTrue(resumen.aprobada());
    }

    @Test
    public void simularParcialDosSieteNoPasaYSeVeDosNoventaYNueve() {
        Materia calculo = calculoII();
        List<Nota> notas = notasCalculoII();
        notas.add(new Nota(3, "parcial", 2.7));
        notas.add(new Nota(3, "auto", 4.5));

        assertEquals(3.15, CalculadoraNotas.notaCorte(calculo.cortes.get(2), notas), DELTA);
        Resumen resumen = CalculadoraNotas.resumen(calculo, notas);
        assertEquals(2.985, resumen.definitiva, DELTA);
        assertFalse("2.985 no pasa: no se redondea (RN-08)", resumen.aprobada());
        assertEquals("2.99", Formato.notaSinFalsasEsperanzas(resumen.definitiva, 3.0));
    }

    @Test
    public void promedioDelSemestreActual() {
        Double promedio = CalculadoraNotas.promedioPonderado(Arrays.asList(
                new NotaConCreditos(2.9, 3),   // Cálculo II
                new NotaConCreditos(3.8, 4),   // Física I
                new NotaConCreditos(4.3, 4),   // Programación I
                new NotaConCreditos(3.4, 2),   // Inglés III
                new NotaConCreditos(3.1, 3),   // Química General
                new NotaConCreditos(4.7, 2))); // Ética
        assertEquals(66.6 / 18, promedio, DELTA);
        assertEquals("3.7", Formato.nota(promedio));
    }

    @Test
    public void promedioDeUnSemestreAnteriorConSoloNotasFinales() {
        List<NotaConCreditos> materias = new ArrayList<>();
        double[][] finales = {{3.1, 4}, {3.4, 3}, {4.0, 3}, {3.6, 3}, {3.5, 3}};
        for (double[] fila : finales) {
            Materia materia = new Materia();
            materia.notaFinal = fila[0];
            materia.creditos = (int) fila[1];
            Double nota = CalculadoraNotas.notaParaPromedio(materia, new ArrayList<>());
            materias.add(new NotaConCreditos(nota, materia.creditos));
        }
        Double promedio = CalculadoraNotas.promedioPonderado(materias);
        assertEquals(55.9 / 16, promedio, DELTA);
        assertEquals("3.5", Formato.nota(promedio));
    }

    // ---------- Bordes ----------

    @Test
    public void sinNotasNoLlevaNadaYNecesitaLaNotaParaPasar() {
        Resumen resumen = CalculadoraNotas.resumen(calculoII(), new ArrayList<>());
        assertNull(resumen.llevas);
        assertFalse(resumen.enRiesgo);
        assertEquals(3.0, resumen.necesitas, DELTA);
    }

    @Test
    public void conCincosEnLosDosPrimerosCortesYaPaso() {
        Resumen resumen = CalculadoraNotas.resumen(calculoII(), cortesUnoYDosCon(5.0));
        assertTrue(resumen.yaPaso());
        assertFalse(resumen.enRiesgo);
    }

    @Test
    public void conCerosEnLosDosPrimerosCortesYaNoAlcanza() {
        Resumen resumen = CalculadoraNotas.resumen(calculoII(), cortesUnoYDosCon(0.0));
        assertTrue(resumen.yaNoAlcanza());
        assertTrue(resumen.enRiesgo);
    }

    @Test
    public void tresEnTodoPasaAunqueLosDecimalesDeJavaDenDosNoventaYNueve() {
        List<Nota> notas = new ArrayList<>();
        for (int corte = 1; corte <= 3; corte++) {
            for (String componente : new String[]{"parcial", "trabajos", "auto"}) {
                notas.add(new Nota(corte, componente, 3.0));
            }
        }
        Resumen resumen = CalculadoraNotas.resumen(calculoII(), notas);
        assertEquals(3.0, resumen.definitiva, DELTA);
        assertTrue(resumen.aprobada());
        assertNull("Con todos los cortes ya no hay cuánto necesitas", resumen.necesitas);
    }

    @Test
    public void siSoloFaltaLaAutoevaluacionSeCalculaLaQueNecesitaSinSuponer() {
        Corte corte3 = calculoII().cortes.get(2);
        List<Nota> notas = notasCalculoII();
        notas.add(new Nota(3, "parcial", 3.0));

        NecesarioEnCorte auto = CalculadoraNotas.necesarioEnCorte(corte3, notas, 3.2);
        assertEquals("(3.2 − 1.8 − 1.08) / 0.1", 3.2, auto.nota, DELTA);
        assertFalse(auto.autoevaluacionSupuesta);
    }

    @Test
    public void unCorteEvaluadoNoNecesitaNada() {
        Corte corte1 = calculoII().cortes.get(0);
        assertNull(CalculadoraNotas.necesarioEnCorte(corte1, notasCalculoII(), 3.2));
    }

    @Test
    public void quizDeQuinceTrabajoDeDiezYTallerDeCincoLlenanLosTreintaDelCorte() {
        Componente trabajos = new Componente("trabajos", "Trabajos y quices", 30, false);
        List<Nota> notas = Arrays.asList(
                new Nota(1, "trabajos", 4.0, 15.0),
                new Nota(1, "trabajos", 3.0, 10.0),
                new Nota(1, "trabajos", 5.0, 5.0));

        // (4.0 × 15 + 3.0 × 10 + 5.0 × 5) / 30 = 3.83
        Double nota = CalculadoraNotas.notaComponente(1, trabajos, notas);
        assertEquals(115.0 / 30, nota, DELTA);
        assertEquals("3.8", Formato.nota(nota));
        assertEquals(30, CalculadoraNotas.pesoRegistrado(1, trabajos, notas), DELTA);
        assertEquals(0, CalculadoraNotas.pesoDisponible(1, trabajos, notas), DELTA);
        assertFalse(CalculadoraNotas.pesosSePasan(1, trabajos, notas));
    }

    @Test
    public void mientrasFaltanPorcentajesCuentaLoRegistrado() {
        Componente trabajos = new Componente("trabajos", "Trabajos y quices", 30, false);
        List<Nota> notas = Arrays.asList(
                new Nota(1, "trabajos", 4.0, 15.0),
                new Nota(1, "trabajos", 3.0, 10.0));

        // (4.0 × 15 + 3.0 × 10) / 25 = 3.6; quedan 5 % por registrar
        assertEquals(3.6, CalculadoraNotas.notaComponente(1, trabajos, notas), DELTA);
        assertEquals(5, CalculadoraNotas.pesoDisponible(1, trabajos, notas), DELTA);
    }

    @Test
    public void pesosQueSePasanDelComponente() {
        Componente trabajos = new Componente("trabajos", "Trabajos y quices", 30, false);
        List<Nota> notas = Arrays.asList(
                new Nota(1, "trabajos", 4.0, 15.0),
                new Nota(1, "trabajos", 3.0, 10.0),
                new Nota(1, "trabajos", 3.5, 10.0));

        assertEquals(-5, CalculadoraNotas.pesoDisponible(1, trabajos, notas), DELTA);
        assertTrue(CalculadoraNotas.pesosSePasan(1, trabajos, notas));
    }

    /**
     * El caso de la pantalla: Corte 3 de Cálculo II con meta 3.2 y trabajos "Con
     * porcentajes" en 5 + 10 + 5 % del corte (3.8, 3.4 y 3.6 → 3.55).
     */
    @Test
    public void corteTresConTrabajosConPorcentajesComoEnLaPantalla() {
        Materia calculo = calculoII();
        Corte corte3 = calculo.cortes.get(2);
        corte3.componentes.get(1).promedioIgual = false;
        List<Nota> notas = notasCortesUnoYDos();
        notas.add(new Nota(3, "trabajos", 3.8, 5.0));
        notas.add(new Nota(3, "trabajos", 3.4, 10.0));
        notas.add(new Nota(3, "trabajos", 3.6, 5.0));

        // Meta del corte: (3.0 − 2.7 × 0.33 − 3.1 × 0.33) / 0.34 = 3.19 → 3.2
        double meta = CalculadoraNotas.resumen(calculo, notas).necesitas;
        assertEquals(3.2, meta, DELTA);

        // Los trabajos aportan lo registrado (71 / 100 = 0.71) y el 10 % que falta queda
        // pendiente, sin suponer nada: (3.2 − 0.71 − 4.5 × 0.10) / 0.70 = 2.91 → 3.0 en el
        // parcial y en ese 10 %
        NecesarioEnCorte parcial = CalculadoraNotas.necesarioEnCorte(corte3, notas, meta);
        assertEquals(3.0, parcial.nota, DELTA);
        assertTrue(parcial.autoevaluacionSupuesta);
        assertEquals(2, parcial.enQue.size());
        assertEquals("parcial", parcial.enQue.get(0).componenteId);
        assertEquals("trabajos", parcial.enQue.get(1).componenteId);
        assertEquals(10, parcial.enQue.get(1).porcentaje, DELTA);
        assertTrue(parcial.enQue.get(1).parcial);

        // Con un 4.5 de trabajo que vale 10 % ya están los 30 %: parcial (3.2 − 1.16 − 0.45) / 0.60 = 2.65 → 2.7
        List<Nota> conTrabajo = new ArrayList<>(notas);
        conTrabajo.add(new Nota(3, "trabajos", 4.5, 10.0));
        NecesarioEnCorte soloParcial = CalculadoraNotas.necesarioEnCorte(corte3, conTrabajo, meta);
        assertEquals(2.7, soloParcial.nota, DELTA);
        assertEquals(1, soloParcial.enQue.size());

        // Con 3.5 en el parcial falta el 10 % de trabajos (y la auto, que se supone en 4.5):
        // (3.2 − 2.1 − 0.71 − 0.45) / 0.10 = −0.6 → ya llega a la meta
        List<Nota> conParcial = new ArrayList<>(notas);
        conParcial.add(new Nota(3, "parcial", 3.5));
        NecesarioEnCorte falta = CalculadoraNotas.necesarioEnCorte(corte3, conParcial, meta);
        assertEquals(-0.6, falta.nota, DELTA);
        assertEquals("trabajos", falta.enQue.get(0).componenteId);

        // Mientras a trabajos le falte %, el corte no está evaluado, aunque tenga todo lo
        // demás: con 3.0 en auto, (3.2 − 2.1 − 0.71 − 0.30) / 0.10 = 0.9 en ese 10 %
        conParcial.add(new Nota(3, "auto", 3.0));
        assertFalse(CalculadoraNotas.corteEvaluado(corte3, conParcial));
        assertEquals(0.9, CalculadoraNotas.necesarioEnCorte(corte3, conParcial, meta).nota, DELTA);
    }

    @Test
    public void lasNotasDeOtroCorteNoCuentanEnElPeso() {
        Componente trabajos = new Componente("trabajos", "Trabajos y quices", 30, false);
        List<Nota> notas = Arrays.asList(
                new Nota(1, "trabajos", 4.0, 15.0),
                new Nota(2, "trabajos", 3.0, 20.0));

        assertEquals(15, CalculadoraNotas.pesoRegistrado(1, trabajos, notas), DELTA);
    }

    @Test
    public void conPorcentajesPeroSinPesosUsaElPromedioSimple() {
        Componente trabajos = new Componente("trabajos", "Trabajos y quices", 30, false);
        List<Nota> notas = Arrays.asList(new Nota(1, "trabajos", 3.0), new Nota(1, "trabajos", 4.0));
        assertEquals(3.5, CalculadoraNotas.notaComponente(1, trabajos, notas), DELTA);
    }

    @Test
    public void elPromedioIgnoraLasMateriasSinNota() {
        Double promedio = CalculadoraNotas.promedioPonderado(Arrays.asList(
                new NotaConCreditos(4.0, 3), new NotaConCreditos(null, 4)));
        assertEquals(4.0, promedio, DELTA);
        assertNull(CalculadoraNotas.promedioPonderado(new ArrayList<>()));
    }

    @Test
    public void redondearArribaNoSeDejaEnganarPorLosDecimalesDeJava() {
        assertEquals(0.3, CalculadoraNotas.redondearArriba(0.1 + 0.2), DELTA);
        assertEquals(3.2, CalculadoraNotas.redondearArriba(3.194), DELTA);
        assertEquals(3.2, CalculadoraNotas.redondearArriba(3.2), DELTA);
    }

    @Test
    public void dosNoventaYNueveNoPasa() {
        assertFalse(CalculadoraNotas.aprueba(2.99, 3.0));
        assertTrue(CalculadoraNotas.aprueba(3.0, 3.0));
    }

    // ---------- Datos del ejemplo ----------

    /** Cálculo II: 3 cortes de 33, 33 y 34 %, con la forma de calificar por defecto. */
    private static Materia calculoII() {
        Materia materia = new Materia();
        materia.nombre = "Cálculo II";
        materia.creditos = 3;
        materia.notaParaPasar = 3.0;
        materia.cortes = Arrays.asList(corte(1, 33), corte(2, 33), corte(3, 34));
        return materia;
    }

    private static Corte corte(int numero, double porcentaje) {
        return new Corte(numero, porcentaje, new ArrayList<>(Arrays.asList(
                new Componente("parcial", "Parcial", 60, true),
                new Componente("trabajos", "Trabajos y quices", 30, true),
                new Componente("auto", "Autoevaluación", 10, true))));
    }

    /** Las notas del documento: cortes 1 y 2 completos y el 3 solo con trabajos. */
    private static List<Nota> notasCalculoII() {
        List<Nota> notas = notasCortesUnoYDos();
        notas.add(new Nota(3, "trabajos", 3.8));
        notas.add(new Nota(3, "trabajos", 3.4));
        notas.add(new Nota(3, "trabajos", 3.6));
        return notas;
    }

    private static List<Nota> notasCortesUnoYDos() {
        return new ArrayList<>(Arrays.asList(
                new Nota(1, "parcial", 2.0),
                new Nota(1, "trabajos", 3.0),
                new Nota(1, "trabajos", 4.0),
                new Nota(1, "auto", 4.5),
                new Nota(2, "parcial", 2.8),
                new Nota(2, "trabajos", 3.0),
                new Nota(2, "trabajos", 3.4),
                new Nota(2, "auto", 4.6)));
    }

    private static List<Nota> cortesUnoYDosCon(double valor) {
        List<Nota> notas = new ArrayList<>();
        for (int corte = 1; corte <= 2; corte++) {
            for (String componente : new String[]{"parcial", "trabajos", "auto"}) {
                notas.add(new Nota(corte, componente, valor));
            }
        }
        return notas;
    }
}
