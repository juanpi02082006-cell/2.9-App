package com.dosnueve.app.ui.onboarding;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * 02 ¿Dónde estudias? (doc 03, RF-03).
 *
 * <p>Selección única entre las instituciones del SNIES ({@link Universidad#todas}), con
 * buscador. Se puede saltar o configurar la forma de calificar a mano (pantalla 07).
 */
public class UniversidadFragment extends Fragment {

    private UniversidadAdapter adaptador;
    @Nullable
    private Universidad elegida;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_universidad, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        MaterialButton botonListo = vista.findViewById(R.id.botonListo);

        adaptador = new UniversidadAdapter(universidad -> {
            elegida = universidad;
            botonListo.setEnabled(true);
        });
        RecyclerView lista = vista.findViewById(R.id.listaUniversidades);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adaptador);
        List<Universidad> todas = Universidad.todas(requireContext());
        adaptador.mostrar(todas);

        // Filtra mientras se escribe; sin tildes ni mayúsculas ("bogota" encuentra Bogotá)
        TextInputEditText buscar = vista.findViewById(R.id.buscar);
        buscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                List<Universidad> encontradas = new ArrayList<>();
                for (Universidad universidad : todas) {
                    if (universidad.coincide(texto.toString())) {
                        encontradas.add(universidad);
                    }
                }
                adaptador.mostrar(encontradas);
                lista.scrollToPosition(0);
            }
        });

        // Se saluda con el nombre de la cuenta; si todavía no hay, sin saludo
        TextView saludo = vista.findViewById(R.id.saludo);
        String nombre = Repositorio.get().nombre();
        saludo.setVisibility(nombre.isEmpty() ? View.GONE : View.VISIBLE);
        saludo.setText(getString(R.string.universidad_saludo, nombre));

        botonListo.setOnClickListener(v -> {
            if (elegida != null) {
                // Su forma de calificar pasa a ser la plantilla de las materias nuevas
                Repositorio.get().elegirUniversidad(String.valueOf(elegida.id), elegida.nombre,
                        elegida.cortes, elegida.notaParaPasar);
            }
            irAlInicio();
        });
        vista.findViewById(R.id.botonSaltar).setOnClickListener(v -> {
            Repositorio.get().empezarSinUniversidad();
            irAlInicio();
        });
        vista.findViewById(R.id.botonConfigurar).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.formaCalificarFragment));
    }

    /**
     * Entra a la app dejando atrás el login: se sacan Bienvenida y esta pantalla del
     * historial, para que el botón atrás desde el Inicio salga de la app en vez de
     * devolver a escoger universidad otra vez.
     */
    private void irAlInicio() {
        NavOptions sinVolverAlLogin = new NavOptions.Builder()
                .setPopUpTo(R.id.bienvenidaFragment, true, false)
                .build();
        NavHostFragment.findNavController(this)
                .navigate(R.id.inicioFragment, null, sinVolverAlLogin);
    }
}
