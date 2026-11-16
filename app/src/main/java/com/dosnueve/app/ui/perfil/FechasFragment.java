package com.dosnueve.app.ui.perfil;

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
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 24 Fechas del semestre (doc 03, RF-27).
 *
 * <p>Con la fecha de inicio y la de fin, la app calcula en qué semana va y lo muestra
 * con una barra. Esa semana es la que sale arriba del Inicio (pantalla 03).
 */
public class FechasFragment extends Fragment {

    private static final DateTimeFormatter COMO_SE_LEE =
            DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "CO"));

    private LocalDate inicio = Repositorio.get().inicioSemestre();
    private LocalDate fin = Repositorio.get().finSemestre();

    private View botonGuardar;
    private TextInputEditText campoInicio;
    private TextInputEditText campoFin;
    private TextView semana;
    private View barraLlena;
    private View barraVacia;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_fechas, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        campoInicio = vista.findViewById(R.id.fechaInicio);
        campoFin = vista.findViewById(R.id.fechaFin);
        semana = vista.findViewById(R.id.semana);
        barraLlena = vista.findViewById(R.id.barraSemanaLlena);
        barraVacia = vista.findViewById(R.id.barraSemanaVacia);

        campoInicio.setOnClickListener(v -> escogerFecha(true));
        campoFin.setOnClickListener(v -> escogerFecha(false));

        botonGuardar = vista.findViewById(R.id.botonGuardar);
        vista.findViewById(R.id.botonVolver).setOnClickListener(v -> volver());
        botonGuardar.setOnClickListener(v -> {
            Repositorio.get().guardarFechas(inicio, fin);
            volver();
        });

        refrescar();
    }

    private void escogerFecha(boolean esLaDeInicio) {
        LocalDate actual = esLaDeInicio ? inicio : fin;

        MaterialDatePicker<Long> calendario = MaterialDatePicker.Builder.datePicker()
                .setTitleText(esLaDeInicio ? R.string.fechas_inicio : R.string.fechas_fin)
                .setSelection(actual.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
                .build();

        calendario.addOnPositiveButtonClickListener(milisegundos -> {
            LocalDate escogida = Instant.ofEpochMilli(milisegundos)
                    .atZone(ZoneId.of("UTC")).toLocalDate();
            if (esLaDeInicio) {
                inicio = escogida;
            } else {
                fin = escogida;
            }
            refrescar();
        });
        calendario.show(getParentFragmentManager(), "calendario");
    }

    private void refrescar() {
        campoInicio.setText(inicio.format(COMO_SE_LEE));
        campoFin.setText(fin.format(COMO_SE_LEE));

        // Si termina antes de empezar no hay semestre que guardar
        botonGuardar.setEnabled(fin.isAfter(inicio));

        int semanasTotales = Repositorio.semanasEntre(inicio, fin);
        int semanaActual = Math.max(1,
                Math.min(semanasTotales, Repositorio.semanasEntre(inicio, LocalDate.now())));

        semana.setText(getString(R.string.fechas_semana, semanaActual, semanasTotales));
        ponerPeso(barraLlena, semanaActual);
        ponerPeso(barraVacia, Math.max(0, semanasTotales - semanaActual));
    }

    private void ponerPeso(View vista, float peso) {
        LinearLayout.LayoutParams medidas = (LinearLayout.LayoutParams) vista.getLayoutParams();
        medidas.weight = peso;
        vista.setLayoutParams(medidas);
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
