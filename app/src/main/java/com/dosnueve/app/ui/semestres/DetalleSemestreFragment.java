package com.dosnueve.app.ui.semestres;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Semestre;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.ui.inicio.PlanillaAdapter;
import com.dosnueve.app.util.Formato;

import java.util.ArrayList;
import java.util.List;

/**
 * 23 Detalle de semestre (doc 03, RF-26).
 *
 * <p>Planilla con la nota de cada materia: la final en los semestres terminados (RN-09) y
 * lo que lleva en el que va en curso. De un terminado se puede ir a editar sus notas
 * finales (pantalla 22); del en curso, al detalle de cada materia.
 */
public class DetalleSemestreFragment extends Fragment {

    private static final String ARG_SEMESTRE = "semestreId";

    /** Lo que se pinta de cada materia. */
    private static class Fila {

        final String nombre;
        final int creditos;
        final Double nota;
        final double notaParaPasar;
        /** Solo en el semestre en curso, para ir al detalle de la materia. */
        @Nullable
        final String materiaId;

        Fila(String nombre, int creditos, Double nota, double notaParaPasar, @Nullable String materiaId) {
            this.nombre = nombre;
            this.creditos = creditos;
            this.nota = nota;
            this.notaParaPasar = notaParaPasar;
            this.materiaId = materiaId;
        }
    }

    private long semestreId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_detalle_semestre, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        semestreId = requireArguments().getLong(ARG_SEMESTRE);

        vista.findViewById(R.id.botonVolver).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        vista.findViewById(R.id.botonEditarFinales).setOnClickListener(v -> {
            Bundle argumentos = new Bundle();
            argumentos.putLong(ARG_SEMESTRE, semestreId);
            NavHostFragment.findNavController(this).navigate(R.id.semestreAnteriorFragment, argumentos);
        });

        Repositorio.get().cambios().observe(getViewLifecycleOwner(), cambio -> pintar(vista));
    }

    private void pintar(View vista) {
        Repositorio datos = Repositorio.get();
        boolean enCurso = semestreId == SemestresFragment.EN_CURSO;
        List<Fila> filas = new ArrayList<>();
        String periodo;

        if (enCurso) {
            periodo = datos.periodoActual();
            for (Materia materia : datos.materias()) {
                filas.add(new Fila(materia.nombre, materia.creditos,
                        CalculadoraNotas.notaParaPromedio(materia, datos.notas(materia.id)),
                        materia.notaParaPasar, materia.id));
            }
        } else {
            Semestre semestre = datos.anterior(String.valueOf(semestreId));
            if (semestre == null) {
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }
            periodo = semestre.periodo;
            for (Materia materia : semestre.materias) {
                filas.add(new Fila(materia.nombre, materia.creditos, materia.notaFinal,
                        materia.notaParaPasar, null));
            }
        }

        List<CalculadoraNotas.NotaConCreditos> conCreditos = new ArrayList<>();
        int creditos = 0;
        for (Fila fila : filas) {
            conCreditos.add(new CalculadoraNotas.NotaConCreditos(fila.nota, fila.creditos));
            creditos += fila.creditos;
        }

        ((TextView) vista.findViewById(R.id.periodo)).setText(periodo);
        ((TextView) vista.findViewById(R.id.detalle)).setText(getString(enCurso
                ? R.string.semestre_detalle_en_curso
                : R.string.semestre_detalle_terminado, filas.size(), creditos));
        ((TextView) vista.findViewById(R.id.promedio))
                .setText(Formato.nota(CalculadoraNotas.promedioPonderado(conCreditos)));
        ((TextView) vista.findViewById(R.id.encabezadoNota)).setText(enCurso
                ? R.string.planilla_definitiva
                : R.string.planilla_definitiva_final);
        vista.findViewById(R.id.botonEditarFinales).setVisibility(enCurso ? View.GONE : View.VISIBLE);

        pintarPlanilla(vista, filas);
    }

    private void pintarPlanilla(View vista, List<Fila> filas) {
        LinearLayout lista = vista.findViewById(R.id.listaMaterias);
        lista.removeAllViews();

        for (int i = 0; i < filas.size(); i++) {
            Fila fila = filas.get(i);
            View renglon = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_planilla, lista, false);

            TextView nombre = renglon.findViewById(R.id.nombre);
            TextView creditos = renglon.findViewById(R.id.creditos);
            TextView definitiva = renglon.findViewById(R.id.definitiva);

            nombre.setText(fila.nombre);
            creditos.setText(getString(R.string.detalle_creditos, fila.creditos));
            definitiva.setText(Formato.notaSinFalsasEsperanzas(fila.nota, fila.notaParaPasar));
            boolean aprobo = fila.nota != null && CalculadoraNotas.aprueba(fila.nota, fila.notaParaPasar);
            PlanillaAdapter.pintarCasilla(definitiva, fila.nota != null && !aprobo, aprobo);

            // La planilla del detalle no lleva cortes: un semestre terminado no los tiene (RN-09)
            renglon.findViewById(R.id.corte1).setVisibility(View.GONE);
            renglon.findViewById(R.id.corte2).setVisibility(View.GONE);
            renglon.findViewById(R.id.corte3).setVisibility(View.GONE);
            renglon.findViewById(R.id.divisor)
                    .setVisibility(i == filas.size() - 1 ? View.GONE : View.VISIBLE);

            if (fila.materiaId != null) {
                renglon.setOnClickListener(v -> {
                    Bundle argumentos = new Bundle();
                    argumentos.putLong("materiaId", Long.parseLong(fila.materiaId));
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.detalleMateriaFragment, argumentos);
                });
            }
            lista.addView(renglon);
        }
    }
}
