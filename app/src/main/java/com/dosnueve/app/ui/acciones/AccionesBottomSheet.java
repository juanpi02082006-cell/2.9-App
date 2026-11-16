package com.dosnueve.app.ui.acciones;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * 04 Acciones rápidas (doc 03).
 *
 * <p>Sale del botón + de la barra. "Registrar una nota" va primero porque es el
 * camino de cuatro toques desde el Inicio (RNF-03).
 */
public class AccionesBottomSheet extends BottomSheetDialogFragment {

    public static final String ETIQUETA = "acciones";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.hoja_acciones, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        // Sin materias no hay dónde registrar: primero se crea una
        vista.findViewById(R.id.accionRegistrarNota).setOnClickListener(v -> {
            if (Repositorio.get().materias().isEmpty()) {
                Toast.makeText(requireContext(), R.string.nota_sin_materias, Toast.LENGTH_SHORT).show();
                irA(R.id.nuevaMateriaFragment);
            } else {
                irA(R.id.registrarNotaFragment);
            }
        });
        vista.findViewById(R.id.accionNuevaMateria)
                .setOnClickListener(v -> irA(R.id.nuevaMateriaFragment));
        vista.findViewById(R.id.botonCancelar).setOnClickListener(v -> dismiss());
    }

    private void irA(int destino) {
        // La hoja vive sobre la Activity, así que se navega con el contenedor del host
        Navigation.findNavController(requireActivity(), R.id.contenedorNav).navigate(destino);
        dismiss();
    }
}
