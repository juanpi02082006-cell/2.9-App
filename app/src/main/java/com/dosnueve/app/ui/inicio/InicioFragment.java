package com.dosnueve.app.ui.inicio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Conexion;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.ui.perfil.Avatar;
import com.dosnueve.app.util.Formato;

import java.util.ArrayList;
import java.util.List;

/**
 * 03 Inicio · 18 Inicio vacío (doc 03).
 *
 * <p>Las materias salen del {@link Repositorio} y cada número de la planilla lo calcula
 * {@link CalculadoraNotas} al pintar (RN-12). Se repinta solo cuando algo cambia, por
 * ejemplo al deshacer una nota desde el aviso de abajo.
 */
public class InicioFragment extends Fragment {

    private PlanillaAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_inicio, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        adaptador = new PlanillaAdapter(this::irAlDetalleDeMateria);

        RecyclerView lista = vista.findViewById(R.id.listaMaterias);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adaptador);

        vista.findViewById(R.id.avatar).setOnClickListener(v -> irAlPerfil());
        vista.findViewById(R.id.botonPrimeraMateria)
                .setOnClickListener(v -> irANuevaMateria());

        Repositorio.get().cambios().observe(getViewLifecycleOwner(), cambio -> mostrar(vista, filas()));

        // Aparece al quedarse sin Internet y se va sola cuando vuelve
        View sinConexion = vista.findViewById(R.id.avisoSinConexion);
        Conexion.hayInternet().observe(getViewLifecycleOwner(), hay ->
                sinConexion.setVisibility(Boolean.FALSE.equals(hay) ? View.VISIBLE : View.GONE));
    }

    private void mostrar(View vista, List<FilaPlanilla> filas) {
        boolean hayMaterias = !filas.isEmpty();
        Repositorio datos = Repositorio.get();

        // El mismo círculo de Perfil: foto de Google, avatar o iniciales
        Avatar.pintar(vista.findViewById(R.id.avatarFoto), vista.findViewById(R.id.avatarIniciales));

        vista.findViewById(R.id.tarjetaPlanilla)
                .setVisibility(hayMaterias ? View.VISIBLE : View.GONE);
        vista.findViewById(R.id.estadoVacio)
                .setVisibility(hayMaterias ? View.GONE : View.VISIBLE);

        TextView promedio = vista.findViewById(R.id.promedio);
        TextView frase = vista.findViewById(R.id.promedioFrase);
        TextView semana = vista.findViewById(R.id.semana);

        TextView enRojo = vista.findViewById(R.id.promedioRiesgo);

        semana.setText(getString(R.string.inicio_semana, datos.semanaActual(), datos.semanasTotales()));

        // Solo cuentan las materias que ya tienen algún corte evaluado
        List<CalculadoraNotas.NotaConCreditos> conCreditos = new ArrayList<>();
        List<String> enRiesgo = new ArrayList<>();
        int conNota = 0;
        for (FilaPlanilla fila : filas) {
            conCreditos.add(new CalculadoraNotas.NotaConCreditos(fila.llevas, fila.creditos));
            if (fila.llevas != null) {
                conNota++;
            }
            if (fila.enRiesgo) {
                enRiesgo.add(fila.nombre);
            }
        }

        Double delSemestre = CalculadoraNotas.promedioPonderado(conCreditos);
        promedio.setText(delSemestre == null
                ? getString(R.string.inicio_promedio_vacio)
                : Formato.nota(delSemestre));
        frase.setText(conNota == 0 ? ""
                : getString(R.string.inicio_promedio_frase, conNota - enRiesgo.size(), conNota));

        // Una materia: se nombra. Varias: solo cuántas, para no llenar el bloque de texto
        enRojo.setVisibility(enRiesgo.isEmpty() ? View.GONE : View.VISIBLE);
        if (enRiesgo.size() == 1) {
            enRojo.setText(getString(R.string.inicio_en_rojo_una, enRiesgo.get(0)));
        } else if (!enRiesgo.isEmpty()) {
            enRojo.setText(getString(R.string.inicio_en_rojo_varias, enRiesgo.size()));
        }
        adaptador.mostrar(filas);
    }

    /** Una fila por materia, con la nota de sus tres primeros cortes y lo que lleva. */
    private List<FilaPlanilla> filas() {
        Repositorio datos = Repositorio.get();
        List<FilaPlanilla> filas = new ArrayList<>();
        for (Materia materia : datos.materias()) {
            List<Nota> notas = datos.notas(materia.id);
            Double[] notasCortes = new Double[materia.cortes.size()];
            for (int i = 0; i < notasCortes.length; i++) {
                notasCortes[i] = CalculadoraNotas.notaCorte(materia.cortes.get(i), notas);
            }
            CalculadoraNotas.Resumen resumen = CalculadoraNotas.resumen(materia, notas);
            filas.add(new FilaPlanilla(Long.parseLong(materia.id), materia.nombre, materia.creditos,
                    notasCortes, resumen.llevas, resumen.enRiesgo, materia.notaParaPasar));
        }
        return filas;
    }

    private void irAlDetalleDeMateria(long materiaId) {
        Bundle argumentos = new Bundle();
        argumentos.putLong("materiaId", materiaId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.detalleMateriaFragment, argumentos);
    }

    private void irAlPerfil() {
        NavHostFragment.findNavController(this).navigate(R.id.perfilFragment);
    }

    private void irANuevaMateria() {
        NavHostFragment.findNavController(this).navigate(R.id.nuevaMateriaFragment);
    }
}
