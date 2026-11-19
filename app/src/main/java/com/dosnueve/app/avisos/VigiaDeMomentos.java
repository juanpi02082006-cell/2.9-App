package com.dosnueve.app.avisos;

import android.content.Context;
import android.content.SharedPreferences;

import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.domain.Momento;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Se da cuenta de cuándo se cierra un corte o el semestre, para celebrarlo una sola vez.
 * Igual que {@link VigiaDeRiesgo}, recuerda en el celular lo que ya vio:
 * <ul>
 *     <li>cada corte se celebra una vez: si después se edita una nota, no se repite;</li>
 *     <li>lo que ya estaba cerrado cuando la app lo ve por primera vez (por ejemplo, al
 *     entrar en un celular nuevo) solo se anota;</li>
 *     <li>el semestre se cierra cuando todas sus materias tienen definitiva.</li>
 * </ul>
 */
public final class VigiaDeMomentos {

    private static final String ARCHIVO = "momentos";
    private static final int SIN_VER = -1;
    private static final int ABIERTO = 0;
    private static final int CERRADO = 1;

    private VigiaDeMomentos() {
    }

    /**
     * @param cargadas materias cuyas notas ya llegaron: las demás todavía no se pueden juzgar
     * @return lo que hay que celebrar ahora, en orden (los cortes primero y el semestre al final)
     */
    public static List<Momento> revisar(Context contexto, String uid, String periodo, int semanas,
                                        List<Materia> materias, Map<String, List<Nota>> notas,
                                        Set<String> cargadas) {
        SharedPreferences guardado = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
        Set<String> conocidas = new HashSet<>(guardado.getStringSet("materias_" + uid, Collections.emptySet()));
        Set<String> celebrados = new HashSet<>(guardado.getStringSet("cortes_" + uid, Collections.emptySet()));
        List<Momento> nuevos = new ArrayList<>();
        boolean cambio = false;

        boolean todasCargadas = !materias.isEmpty();
        boolean todasCerradas = !materias.isEmpty();
        List<CalculadoraNotas.NotaConCreditos> definitivas = new ArrayList<>();
        int aprobadas = 0;
        String mejorMateria = "";
        double mejorNota = -1;

        for (Materia materia : materias) {
            if (!cargadas.contains(materia.id)) {
                todasCargadas = false;
                continue;
            }
            List<Nota> susNotas = notas.get(materia.id);
            if (susNotas == null) {
                susNotas = Collections.emptyList();
            }
            boolean primeraVez = conocidas.add(materia.id);
            cambio |= primeraVez;

            for (Corte corte : materia.cortes) {
                Double notaCorte = CalculadoraNotas.notaCorte(corte, susNotas);
                if (notaCorte == null || !celebrados.add(materia.id + "#" + corte.numero)) {
                    continue;
                }
                cambio = true;
                if (!primeraVez) {
                    nuevos.add(Momento.deCorte(materia.nombre, corte.numero, notaCorte,
                            materia.notaParaPasar, CalculadoraNotas.resumen(materia, susNotas)));
                }
            }

            Double definitiva = CalculadoraNotas.resumen(materia, susNotas).definitiva;
            if (definitiva == null) {
                todasCerradas = false;
            } else {
                definitivas.add(new CalculadoraNotas.NotaConCreditos(definitiva, materia.creditos));
                if (CalculadoraNotas.aprueba(definitiva, materia.notaParaPasar)) {
                    aprobadas++;
                }
                if (definitiva > mejorNota) {
                    mejorNota = definitiva;
                    mejorMateria = materia.nombre;
                }
            }
        }

        SharedPreferences.Editor cambios = guardado.edit();
        // El semestre solo se juzga con todas sus materias a la vista
        if (todasCargadas) {
            String clave = "semestre_" + uid + "_" + periodo;
            int antes = guardado.getInt(clave, SIN_VER);
            int ahora = todasCerradas ? CERRADO : ABIERTO;
            if (antes == SIN_VER || (antes == ABIERTO && ahora == CERRADO)) {
                cambios.putInt(clave, ahora);
                cambio = true;
                if (antes == ABIERTO) {
                    Double promedio = CalculadoraNotas.promedioPonderado(definitivas);
                    nuevos.add(Momento.deSemestre(periodo, promedio == null ? 0 : promedio,
                            aprobadas, materias.size(), mejorMateria, mejorNota, semanas));
                }
            }
        }

        if (cambio) {
            cambios.putStringSet("materias_" + uid, conocidas)
                    .putStringSet("cortes_" + uid, celebrados)
                    .apply();
        }
        return nuevos;
    }
}
