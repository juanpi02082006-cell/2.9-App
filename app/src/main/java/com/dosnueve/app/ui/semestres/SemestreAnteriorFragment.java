package com.dosnueve.app.ui.semestres;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Semestre;
import com.dosnueve.app.ui.LimiteNota;
import com.dosnueve.app.util.Formato;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * 22 Semestre anterior (doc 03, RF-25).
 *
 * <p>Los semestres pasados solo guardan la nota final de cada materia (RN-09).
 * El promedio se recalcula ponderado por créditos cada vez que se escribe (RN-10). Con
 * {@code semestreId} se editan las notas finales de uno ya guardado.
 */
public class SemestreAnteriorFragment extends Fragment {

    private static final String ARG_SEMESTRE = "semestreId";
    private static final String[] PERIODOS = {"1", "2"};

    private final List<View> filas = new ArrayList<>();
    private LinearLayout listaMaterias;
    private TextView promedio;
    private ChipGroup chipsPeriodo;
    @Nullable
    private Semestre semestre;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_semestre_anterior, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        listaMaterias = vista.findViewById(R.id.listaMaterias);
        promedio = vista.findViewById(R.id.promedio);
        chipsPeriodo = vista.findViewById(R.id.chipsPeriodo);
        filas.clear();

        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_SEMESTRE, 0);
        semestre = id == 0 ? null : Repositorio.get().anterior(String.valueOf(id));

        // "2025-2" → año 2025 y periodo 2
        String[] partes = semestre == null ? new String[0] : semestre.periodo.split("-");
        int periodo = partes.length == 2 && "2".equals(partes[1]) ? 1 : 0;
        for (int i = 0; i < PERIODOS.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(PERIODOS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == periodo);
            chipsPeriodo.addView(chip);
        }
        if (partes.length == 2) {
            ((TextView) vista.findViewById(R.id.ano)).setText(partes[0]);
        }

        if (semestre == null) {
            agregarFila(null);
            agregarFila(null);
        } else {
            for (Materia materia : semestre.materias) {
                agregarFila(materia);
            }
        }

        vista.findViewById(R.id.botonAgregarMateria).setOnClickListener(v -> agregarFila(null));
        vista.findViewById(R.id.botonVolver).setOnClickListener(v -> volver());
        vista.findViewById(R.id.botonGuardar).setOnClickListener(v -> guardar(vista));

        recalcularPromedio();
    }

    private void agregarFila(@Nullable Materia materia) {
        View fila = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_materia_final, listaMaterias, false);
        TextInputEditText nombre = fila.findViewById(R.id.nombre);
        TextInputEditText creditos = fila.findViewById(R.id.creditos);
        TextInputEditText nota = fila.findViewById(R.id.notaFinal);
        // Todas las filas comparten ids: sin esto Android les pondría el mismo texto al rotar
        nombre.setSaveEnabled(false);
        creditos.setSaveEnabled(false);
        nota.setSaveEnabled(false);
        if (materia != null) {
            nombre.setText(materia.nombre);
            creditos.setText(String.valueOf(materia.creditos));
            nota.setText(Formato.nota(materia.notaFinal));
        }

        TextWatcher recalcular = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence texto, int inicio, int cuenta, int despues) {
            }

            @Override
            public void onTextChanged(CharSequence texto, int inicio, int antes, int cuenta) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                recalcularPromedio();
            }
        };
        LimiteNota.vigilar(nota);
        creditos.addTextChangedListener(recalcular);
        nota.addTextChangedListener(recalcular);

        fila.findViewById(R.id.botonQuitar).setOnClickListener(v -> {
            listaMaterias.removeView(fila);
            filas.remove(fila);
            recalcularPromedio();
        });

        filas.add(fila);
        listaMaterias.addView(fila);
    }

    /** promedioSemestre = Σ (nota × créditos) / Σ créditos (doc 02 · 3.7). */
    private void recalcularPromedio() {
        double puntos = 0;
        double creditos = 0;

        for (View fila : filas) {
            Double nota = leerNumero(fila.findViewById(R.id.notaFinal));
            Double susCreditos = leerNumero(fila.findViewById(R.id.creditos));
            if (nota != null && susCreditos != null && susCreditos > 0) {
                puntos += nota * susCreditos;
                creditos += susCreditos;
            }
        }
        promedio.setText(creditos == 0 ? Formato.PENDIENTE : Formato.nota(puntos / creditos));
    }

    /**
     * Se guardan las filas completas (nombre, créditos y una nota de 0.0 a 5.0); las vacías
     * no cuentan. Hace falta el año y al menos una materia.
     */
    private void guardar(View vista) {
        Double ano = leerNumero(vista.findViewById(R.id.ano));
        List<Materia> materias = new ArrayList<>();
        double paraPasar = Repositorio.get().notaParaPasar();
        for (View fila : filas) {
            TextInputEditText campoNombre = fila.findViewById(R.id.nombre);
            String nombre = campoNombre.getText() == null ? "" : campoNombre.getText().toString().trim();
            Double creditos = leerNumero(fila.findViewById(R.id.creditos));
            Double nota = leerNumero(fila.findViewById(R.id.notaFinal));
            if (nombre.isEmpty() || creditos == null || creditos < 1 || nota == null || nota < 0 || nota > 5) {
                continue;
            }
            Materia materia = new Materia();
            materia.nombre = nombre;
            materia.creditos = (int) Math.round(creditos);
            materia.notaFinal = nota;
            materia.notaParaPasar = paraPasar;
            materias.add(materia);
        }
        if (ano == null || ano < 1900 || ano > 2100 || materias.isEmpty()) {
            Toast.makeText(requireContext(), R.string.semestre_anterior_incompleto, Toast.LENGTH_LONG).show();
            return;
        }

        int periodo = 1;
        for (int i = 0; i < chipsPeriodo.getChildCount(); i++) {
            if (((Chip) chipsPeriodo.getChildAt(i)).isChecked()) {
                periodo = i + 1;
            }
        }
        Semestre guardar = new Semestre();
        guardar.id = semestre == null ? null : semestre.id;
        guardar.periodo = Math.round(ano) + "-" + periodo;
        guardar.materias = materias;
        Repositorio.get().guardarAnterior(guardar);
        volver();
    }

    @Nullable
    private Double leerNumero(TextInputEditText campo) {
        CharSequence texto = campo.getText();
        if (texto == null || texto.toString().trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(texto.toString().trim().replace(',', '.'));
        } catch (NumberFormatException sinNumero) {
            return null;
        }
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
