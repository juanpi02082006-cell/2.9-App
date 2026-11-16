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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Semestre;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.util.Formato;

import java.util.ArrayList;
import java.util.List;

/**
 * 10 Semestres (doc 03, RF-24).
 *
 * <p>El promedio acumulado solo usa semestres terminados (RN-11) y va ponderado por
 * créditos (RN-10). La gráfica se arma con vistas y pesos, sin librerías (doc 03).
 */
public class SemestresFragment extends Fragment {

    private static final double ESCALA_MAXIMA = 5.0;
    /** Id del semestre en curso en la lista y en el detalle. */
    static final long EN_CURSO = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_semestres, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        SemestreAdapter adaptador = new SemestreAdapter(this::irAlDetalle);
        RecyclerView lista = vista.findViewById(R.id.listaSemestres);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adaptador);

        vista.findViewById(R.id.botonSemestreAnterior).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.semestreAnteriorFragment));

        Repositorio.get().cambios().observe(getViewLifecycleOwner(), cambio -> {
            List<SemestreAdapter.SemestreEnPantalla> semestres = semestres();
            pintarAcumulado(vista);
            pintarGrafica(vista, semestres);
            adaptador.mostrar(semestres);
        });
    }

    /**
     * Promedio acumulado de la carrera: solo semestres terminados (RN-11), ponderado por
     * los créditos de cada materia (RN-10). Los créditos aprobados son los de las materias
     * que pasaron.
     */
    private void pintarAcumulado(View vista) {
        List<CalculadoraNotas.NotaConCreditos> materias = new ArrayList<>();
        int aprobados = 0;
        for (Semestre semestre : Repositorio.get().anteriores()) {
            for (Materia materia : semestre.materias) {
                materias.add(new CalculadoraNotas.NotaConCreditos(materia.notaFinal, materia.creditos));
                if (materia.notaFinal != null
                        && CalculadoraNotas.aprueba(materia.notaFinal, materia.notaParaPasar)) {
                    aprobados += materia.creditos;
                }
            }
        }

        TextView acumulado = vista.findViewById(R.id.acumulado);
        TextView detalle = vista.findViewById(R.id.acumuladoDetalle);

        acumulado.setText(Formato.nota(CalculadoraNotas.promedioPonderado(materias)));
        detalle.setText(getString(R.string.semestres_acumulado_detalle, aprobados));
    }

    /**
     * Una barra por semestre. La altura es proporcional: el espacio de arriba pesa
     * (5 − nota) y la barra pesa (nota), así no hay que calcular píxeles.
     */
    private void pintarGrafica(View vista, List<SemestreAdapter.SemestreEnPantalla> semestres) {
        LinearLayout grafica = vista.findViewById(R.id.grafica);
        LinearLayout etiquetas = vista.findViewById(R.id.etiquetasGrafica);
        grafica.removeAllViews();
        etiquetas.removeAllViews();

        for (SemestreAdapter.SemestreEnPantalla semestre : semestres) {
            if (semestre.promedio == null) {
                continue;
            }
            View columna = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_barra_semestre, grafica, false);

            TextView valor = columna.findViewById(R.id.valor);
            valor.setText(Formato.nota(semestre.promedio));

            ponerPeso(columna.findViewById(R.id.espacioArriba),
                    (float) (ESCALA_MAXIMA - semestre.promedio));
            ponerPeso(columna.findViewById(R.id.barra), semestre.promedio.floatValue());

            grafica.addView(columna);

            TextView etiqueta = (TextView) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_etiqueta_semestre, etiquetas, false);
            etiqueta.setText(semestre.periodo);
            etiquetas.addView(etiqueta);
        }
    }

    private void ponerPeso(View vista, float peso) {
        LinearLayout.LayoutParams medidas = (LinearLayout.LayoutParams) vista.getLayoutParams();
        medidas.weight = peso;
        vista.setLayoutParams(medidas);
    }

    private void irAlDetalle(long semestreId) {
        Bundle argumentos = new Bundle();
        argumentos.putLong("semestreId", semestreId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.detalleSemestreFragment, argumentos);
    }

    /**
     * Los terminados, del más viejo al más nuevo, y al final el que va en curso (si ya
     * tiene materias). El en curso usa id 0 y su promedio es lo que lleva cada materia.
     */
    private List<SemestreAdapter.SemestreEnPantalla> semestres() {
        Repositorio datos = Repositorio.get();
        List<SemestreAdapter.SemestreEnPantalla> semestres = new ArrayList<>();
        for (Semestre semestre : datos.anteriores()) {
            List<CalculadoraNotas.NotaConCreditos> materias = new ArrayList<>();
            int creditos = 0;
            for (Materia materia : semestre.materias) {
                materias.add(new CalculadoraNotas.NotaConCreditos(materia.notaFinal, materia.creditos));
                creditos += materia.creditos;
            }
            semestres.add(new SemestreAdapter.SemestreEnPantalla(Long.parseLong(semestre.id),
                    semestre.periodo, semestre.materias.size(), creditos,
                    CalculadoraNotas.promedioPonderado(materias), true));
        }

        if (!datos.materias().isEmpty()) {
            List<CalculadoraNotas.NotaConCreditos> materias = new ArrayList<>();
            int creditos = 0;
            for (Materia materia : datos.materias()) {
                materias.add(new CalculadoraNotas.NotaConCreditos(
                        CalculadoraNotas.notaParaPromedio(materia, datos.notas(materia.id)), materia.creditos));
                creditos += materia.creditos;
            }
            semestres.add(new SemestreAdapter.SemestreEnPantalla(EN_CURSO, datos.periodoActual(),
                    datos.materias().size(), creditos, CalculadoraNotas.promedioPonderado(materias),
                    false, datos.semanaActual(), datos.semanasTotales()));
        }
        return semestres;
    }
}
