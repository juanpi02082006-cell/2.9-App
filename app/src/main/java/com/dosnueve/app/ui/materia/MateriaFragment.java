package com.dosnueve.app.ui.materia;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.MainActivity;
import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.util.Formato;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 06 Nueva materia · 15 Editar materia · 16 Borrar materia (doc 03, RF-09 a RF-12).
 *
 * <p>Es el mismo layout en los dos casos: si llega {@code materiaId} se prellena y
 * aparece "Borrar materia". La materia copia la forma de calificar al crearse, así que
 * cambiar la plantilla después no la afecta (RN-05). Al editar, "Cambiar" toca solo la
 * forma de esta materia.
 */
public class MateriaFragment extends Fragment {

    private static final String ARG_MATERIA = "materiaId";
    // El doc 05 define creditos de 1 a 10; antes solo llegaba a 5
    private static final int CREDITOS_MAXIMOS = 10;
    private static final int CREDITOS_POR_DEFECTO = 3;
    private static final int POR_FILA = 5;

    @Nullable
    private Materia materia;
    /** Sobreviven al ir y volver de Forma de calificar, aunque la vista se vuelva a crear. */
    @Nullable
    private Integer creditosElegidos;
    private boolean conProfesor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_materia, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        boolean editando = getArguments() != null && getArguments().containsKey(ARG_MATERIA);
        materia = editando
                ? Repositorio.get().materia(String.valueOf(getArguments().getLong(ARG_MATERIA)))
                : null;
        if (editando && materia == null) {
            volver();
            return;
        }

        TextView titulo = vista.findViewById(R.id.titulo);
        titulo.setText(materia != null ? R.string.materia_titulo_editar : R.string.materia_titulo_nueva);

        View botonBorrar = vista.findViewById(R.id.botonBorrarMateria);
        botonBorrar.setVisibility(materia != null ? View.VISIBLE : View.GONE);
        botonBorrar.setOnClickListener(v -> confirmarBorrado());

        if (materia != null) {
            // Si se vuelve de Forma de calificar, Android repone después lo que se había escrito
            ((TextView) vista.findViewById(R.id.nombre)).setText(materia.nombre);
            ((TextView) vista.findViewById(R.id.profesor)).setText(materia.profesor);
            if (creditosElegidos == null) {
                creditosElegidos = materia.creditos;
            }
            conProfesor = conProfesor || (materia.profesor != null && !materia.profesor.isEmpty());
        }
        llenarCreditos(vista.findViewById(R.id.gridCreditos));

        View bloqueProfesor = vista.findViewById(R.id.bloqueProfesor);
        View botonAgregarProfesor = vista.findViewById(R.id.botonAgregarProfesor);
        bloqueProfesor.setVisibility(conProfesor ? View.VISIBLE : View.GONE);
        botonAgregarProfesor.setVisibility(conProfesor ? View.GONE : View.VISIBLE);
        botonAgregarProfesor.setOnClickListener(v -> {
            conProfesor = true;
            bloqueProfesor.setVisibility(View.VISIBLE);
            botonAgregarProfesor.setVisibility(View.GONE);
        });

        vista.findViewById(R.id.botonVolver).setOnClickListener(v -> volver());
        vista.findViewById(R.id.botonGuardar).setOnClickListener(v -> guardar(vista));

        // Al volver de Forma de calificar, el resumen ya muestra la forma nueva
        Repositorio.get().cambios().observe(getViewLifecycleOwner(), cambio ->
                mostrarResumen(vista.findViewById(R.id.resumenCalificacion)));

        // Una materia nueva empieza por el nombre: el teclado ya abierto ahorra un toque
        if (materia == null && estado == null) {
            View campo = vista.findViewById(R.id.nombre);
            campo.post(() -> {
                if (!isAdded()) {
                    return;
                }
                campo.requestFocus();
                WindowCompat.getInsetsController(requireActivity().getWindow(), campo)
                        .show(WindowInsetsCompat.Type.ime());
            });
        }
    }

    /**
     * Del 1 al 10 en dos filas de 5, todos a la vista. Cada casilla es como los chips de tipo
     * de nota de Registrar nota: un toque la marca y desmarca la que estaba.
     */
    private void llenarCreditos(LinearLayout grilla) {
        grilla.removeAllViews();
        List<View> casillas = new ArrayList<>();
        int espacio = (int) getResources().getDimension(R.dimen.espacio_s);
        LinearLayout fila = null;
        for (int creditos = 1; creditos <= CREDITOS_MAXIMOS; creditos++) {
            if ((creditos - 1) % POR_FILA == 0) {
                fila = new LinearLayout(requireContext());
                fila.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams medidasFila = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (creditos > 1) {
                    medidasFila.topMargin = espacio;
                }
                grilla.addView(fila, medidasFila);
            }
            View casilla = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_chip_tipo, fila, false);
            ((TextView) casilla.findViewById(R.id.nombre)).setText(String.valueOf(creditos));
            casilla.findViewById(R.id.porcentaje).setVisibility(View.GONE);
            casilla.setContentDescription(getString(R.string.detalle_creditos, creditos));

            // Todas del mismo ancho: el número no decide cuánto mide la casilla
            LinearLayout.LayoutParams medidas = (LinearLayout.LayoutParams) casilla.getLayoutParams();
            medidas.width = 0;
            medidas.weight = 1;
            medidas.setMarginStart((creditos - 1) % POR_FILA == 0 ? 0 : espacio);
            casilla.setMinimumHeight((int) getResources().getDimension(R.dimen.area_tactil_min));
            fila.addView(casilla, medidas);
            casillas.add(casilla);

            final int estos = creditos;
            casilla.setOnClickListener(v -> {
                creditosElegidos = estos;
                marcarCreditos(casillas);
            });
        }
        if (creditosElegidos == null) {
            creditosElegidos = CREDITOS_POR_DEFECTO;
        }
        marcarCreditos(casillas);
    }

    private void marcarCreditos(List<View> casillas) {
        for (int i = 0; i < casillas.size(); i++) {
            View casilla = casillas.get(i);
            boolean elegida = creditosElegidos != null && creditosElegidos == i + 1;
            casilla.setSelected(elegida);
            casilla.findViewById(R.id.nombre).setSelected(elegida);
        }
    }

    /**
     * Resumen de la forma de calificar: la de la materia al editar, o la plantilla que va a
     * copiar al crearla (RF-12, RN-05).
     *
     * <p>La barra reparte el ancho según el peso de cada corte, con pesos de layout:
     * así la proporción es exacta sin calcular píxeles.
     */
    private void mostrarResumen(View resumen) {
        Repositorio datos = Repositorio.get();
        List<Corte> cortes = materia != null ? materia.cortes : datos.formaDeCalificar();
        double paraPasar = materia != null ? materia.notaParaPasar : datos.notaParaPasar();

        LinearLayout barra = resumen.findViewById(R.id.barraCortes);
        barra.removeAllViews();
        for (int i = 0; i < cortes.size(); i++) {
            View segmento = new View(requireContext());
            segmento.setBackgroundResource(fondoDelSegmento(i));

            LinearLayout.LayoutParams medidas = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, (float) cortes.get(i).porcentaje);
            if (i > 0) {
                medidas.setMarginStart((int) getResources().getDimension(R.dimen.espacio_xs));
            }
            segmento.setLayoutParams(medidas);
            barra.addView(segmento);
        }

        StringBuilder porcentajes = new StringBuilder();
        for (int i = 0; i < cortes.size(); i++) {
            if (i > 0) {
                porcentajes.append(" · ");
            }
            porcentajes.append(getString(R.string.general_porcentaje,
                    Formato.porcentaje(cortes.get(i).porcentaje)));
        }
        ((TextView) resumen.findViewById(R.id.etiquetaCortes))
                .setText(getString(R.string.materia_cuantos_cortes, cortes.size()));
        ((TextView) resumen.findViewById(R.id.resumenCortes)).setText(porcentajes.toString());
        ((TextView) resumen.findViewById(R.id.resumenTipos))
                .setText(cortes.isEmpty() ? Formato.PENDIENTE : resumenDeTipos(cortes.get(0)));
        ((TextView) resumen.findViewById(R.id.resumenPasar)).setText(Formato.nota(paraPasar));

        resumen.findViewById(R.id.botonCambiarForma).setOnClickListener(v -> {
            Bundle argumentos = new Bundle();
            if (materia != null) {
                argumentos.putLong(ARG_MATERIA, Long.parseLong(materia.id));
            }
            NavHostFragment.findNavController(this).navigate(R.id.formaCalificarFragment, argumentos);
        });
    }

    /** "Parcial 60 · Trab. 30 · Auto 10": corto, para que quepa en una línea. */
    private static String resumenDeTipos(Corte corte) {
        StringBuilder texto = new StringBuilder();
        for (Componente componente : corte.componentes) {
            if (texto.length() > 0) {
                texto.append(" · ");
            }
            String nombre = CalculadoraNotas.ID_TRABAJOS.equals(componente.id) ? "Trab."
                    : CalculadoraNotas.ID_AUTOEVALUACION.equals(componente.id) ? "Auto"
                    : componente.nombre;
            texto.append(nombre).append(' ').append(Formato.porcentaje(componente.porcentaje));
        }
        return texto.toString();
    }

    /** Una sola tinta en tres intensidades, no tres colores con significado propio. */
    private int fondoDelSegmento(int cual) {
        switch (cual % 3) {
            case 0:
                return R.drawable.fondo_segmento_corte_1;
            case 1:
                return R.drawable.fondo_segmento_corte_2;
            default:
                return R.drawable.fondo_segmento_corte_3;
        }
    }

    private void guardar(View vista) {
        TextInputLayout campoNombre = vista.findViewById(R.id.campoNombre);
        String nombre = texto(vista.findViewById(R.id.nombre));
        String profesor = texto(vista.findViewById(R.id.profesor));

        campoNombre.setError(nombre.isEmpty() ? getString(R.string.materia_error_nombre) : null);
        if (nombre.isEmpty()) {
            return;
        }
        int creditos = creditosElegidos == null ? CREDITOS_POR_DEFECTO : creditosElegidos;
        String suProfesor = profesor.isEmpty() ? null : profesor;
        if (materia != null) {
            Repositorio.get().editarMateria(materia.id, nombre, suProfesor, creditos);
            volver();
            return;
        }
        Materia nueva = Repositorio.get().crearMateria(nombre, suProfesor, creditos);
        MainActivity actividad = (MainActivity) requireActivity();
        volver();
        // Lo siguiente casi siempre es registrar la primera nota: queda a un toque en el aviso
        Bundle argumentos = new Bundle();
        argumentos.putLong(ARG_MATERIA, Long.parseLong(nueva.id));
        actividad.avisar(getString(R.string.materia_lista, nueva.nombre),
                getString(R.string.detalle_registrar_nota),
                () -> Navigation.findNavController(actividad, R.id.contenedorNav)
                        .navigate(R.id.registrarNotaFragment, argumentos));
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    /** 16 Borrar materia: se borran sus cortes y notas (RF-12). */
    private void confirmarBorrado() {
        if (materia == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.borrar_materia_titulo, materia.nombre))
                .setMessage(R.string.borrar_materia_efecto)
                .setNegativeButton(R.string.general_cancelar, null)
                .setPositiveButton(R.string.general_borrar, (dialogo, boton) -> {
                    Repositorio.get().borrarMateria(materia.id);
                    NavHostFragment.findNavController(this).popBackStack(R.id.inicioFragment, false);
                })
                .show();
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
