package com.dosnueve.app.avisos;

import android.content.Context;
import android.content.SharedPreferences;

import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Avisa cuando una materia PASA a rojo (RF-32), con la misma cuenta que pinta el Inicio
 * (RN-07). Recuerda en el celular cuáles ya estaban en rojo, así:
 * <ul>
 *     <li>avisa una sola vez, y otra vez solo si sale del rojo y vuelve a entrar;</li>
 *     <li>si el cambio llegó con la app cerrada (desde otro celular), avisa al abrirla;</li>
 *     <li>una materia que ve por primera vez (por ejemplo, al entrar en un celular nuevo)
 *     solo se anota: no avisa de lo que ya estaba en rojo.</li>
 * </ul>
 */
public final class VigiaDeRiesgo {

    private static final String ARCHIVO = "avisos_riesgo";

    private VigiaDeRiesgo() {
    }

    /**
     * @param cargadas materias cuyas notas ya llegaron de Firestore: las demás todavía
     *                 no se pueden juzgar (sin notas no estarían en rojo, y sería falso).
     * @param avisar   si el interruptor de Perfil está encendido. Con él apagado se sigue
     *                 anotando, para no avisar de golpe lo viejo al encenderlo.
     */
    public static void revisar(Context contexto, String uid, List<Materia> materias,
                               Map<String, List<Nota>> notas, Set<String> cargadas, boolean avisar) {
        SharedPreferences guardado = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
        Set<String> conocidas = new HashSet<>(guardado.getStringSet("conocidas_" + uid, Collections.emptySet()));
        Set<String> enRojo = new HashSet<>(guardado.getStringSet("enRojo_" + uid, Collections.emptySet()));
        boolean cambio = false;

        for (Materia materia : materias) {
            if (!cargadas.contains(materia.id)) {
                continue;
            }
            List<Nota> susNotas = notas.get(materia.id);
            CalculadoraNotas.Resumen resumen = CalculadoraNotas.resumen(materia,
                    susNotas == null ? Collections.emptyList() : susNotas);
            boolean rojo = resumen.enRiesgo;
            boolean estaba = enRojo.contains(materia.id);

            if (conocidas.add(materia.id)) {
                cambio = true;
                if (rojo) {
                    enRojo.add(materia.id);
                }
            } else if (rojo && !estaba) {
                enRojo.add(materia.id);
                cambio = true;
                if (avisar && resumen.llevas != null) {
                    Avisos.mostrarRiesgo(contexto, materia, resumen.llevas);
                }
            } else if (!rojo && estaba) {
                enRojo.remove(materia.id);
                cambio = true;
            }
        }

        if (cambio) {
            guardado.edit()
                    .putStringSet("conocidas_" + uid, conocidas)
                    .putStringSet("enRojo_" + uid, enRojo)
                    .apply();
        }
    }
}
