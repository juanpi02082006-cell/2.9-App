package com.dosnueve.app.ui.materia;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.ui.LimiteNota;
import com.dosnueve.app.util.Formato;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 07 Forma de calificar (doc 03, RF-06 a RF-08).
 *
 * <p>Sin {@code materiaId} edita la plantilla que copian las materias nuevas (RN-05); con
 * una materia, solo la forma de esa materia. Guardar solo se habilita cuando los
 * porcentajes de los cortes y los de los tipos de nota de cada corte suman 100 %
 * (RN-02, RN-03).
 */
public class FormaCalificarFragment extends Fragment {

    private static final String ARG_MATERIA = "materiaId";

    /** Un tipo de nota mientras se edita. El porcentaje va como se escribió. */
    private static class Tipo {

        final String id;
        final String nombre;
        String porcentaje;
        final boolean promedioIgual;

        Tipo(String id, String nombre, String porcentaje, boolean promedioIgual) {
            this.id = id;
            this.nombre = nombre;
            this.porcentaje = porcentaje;
            this.promedioIgual = promedioIgual;
        }

        Tipo copia() {
            return new Tipo(id, nombre, porcentaje, promedioIgual);
        }
    }

    // Se quedan en el Fragment: sobreviven aunque la vista se vuelva a crear
    @Nullable
    private Materia materia;
    private boolean cargado;
    private final List<String> cortes = new ArrayList<>();
    private final List<List<Tipo>> tiposPorCorte = new ArrayList<>();
    private boolean igualEnCortes = true;
    private int corteAConfigurar;
    private String notaParaPasar = "";

    private LinearLayout listaCortes;
    private LinearLayout listaTipos;
    private TextView totalCortes;
    private TextView totalTipos;
    private TextView etiquetaIgual;
    private ChipGroup chipsCorte;
    private View errorSuma;
    private View botonGuardar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_forma_calificar, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        listaCortes = vista.findViewById(R.id.listaCortes);
        listaTipos = vista.findViewById(R.id.listaTipos);
        totalCortes = vista.findViewById(R.id.totalCortes);
        totalTipos = vista.findViewById(R.id.totalTipos);
        etiquetaIgual = vista.findViewById(R.id.etiquetaIgualEnCortes);
        chipsCorte = vista.findViewById(R.id.chipsCortePorConfigurar);
        errorSuma = vista.findViewById(R.id.errorSuma);
        botonGuardar = vista.findViewById(R.id.botonGuardar);

        if (!cargado) {
            cargar();
        }

        // RF-07: por defecto la composición es igual en todos los cortes
        MaterialSwitch igual = vista.findViewById(R.id.interruptorIgualEnCortes);
        igual.setChecked(igualEnCortes);
        chipsCorte.setVisibility(igualEnCortes ? View.GONE : View.VISIBLE);
        igual.setOnCheckedChangeListener((boton, encendido) -> {
            igualEnCortes = encendido;
            if (encendido) {
                // Todos los cortes quedan como el que se estaba mirando
                List<Tipo> modelo = tiposPorCorte.get(corteAConfigurar);
                for (int i = 0; i < tiposPorCorte.size(); i++) {
                    tiposPorCorte.set(i, copiar(modelo));
                }
                corteAConfigurar = 0;
            }
            chipsCorte.setVisibility(encendido ? View.GONE : View.VISIBLE);
            pintarTipos();
            revisarTotales();
        });

        EditText paraPasar = vista.findViewById(R.id.notaParaPasar);
        paraPasar.setText(notaParaPasar);
        LimiteNota.vigilar(paraPasar);
        paraPasar.addTextChangedListener(alEscribir(texto -> notaParaPasar = texto));

        vista.findViewById(R.id.botonAgregarCorte).setOnClickListener(v -> agregarCorte());
        vista.findViewById(R.id.botonAgregarTipo).setOnClickListener(v -> pedirNombreDeTipo());
        vista.findViewById(R.id.botonVolver).setOnClickListener(v -> volver());
        botonGuardar.setOnClickListener(v -> guardar(vista));

        pintarCortes();
        pintarTipos();
        revisarTotales();
    }

    /** Lee la forma de la materia o la plantilla, una sola vez. */
    private void cargar() {
        cargado = true;
        Repositorio datos = Repositorio.get();
        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_MATERIA, 0);
        materia = id == 0 ? null : datos.materia(String.valueOf(id));
        List<Corte> forma = materia != null ? Repositorio.copiar(materia.cortes) : datos.formaDeCalificar();
        double pasa = materia != null ? materia.notaParaPasar : datos.notaParaPasar();

        for (Corte corte : forma) {
            cortes.add(Formato.porcentaje(corte.porcentaje));
            List<Tipo> tipos = new ArrayList<>();
            for (Componente componente : corte.componentes) {
                tipos.add(new Tipo(componente.id, componente.nombre,
                        Formato.porcentaje(componente.porcentaje), componente.promedioIgual));
            }
            tiposPorCorte.add(tipos);
        }
        if (cortes.isEmpty()) {
            cortes.add("100");
            tiposPorCorte.add(new ArrayList<>());
        }
        igualEnCortes = todosIguales();
        notaParaPasar = Formato.nota(pasa);
    }

    private boolean todosIguales() {
        List<Tipo> primero = tiposPorCorte.get(0);
        for (List<Tipo> tipos : tiposPorCorte) {
            if (tipos.size() != primero.size()) {
                return false;
            }
            for (int i = 0; i < tipos.size(); i++) {
                if (!tipos.get(i).id.equals(primero.get(i).id)
                        || !tipos.get(i).porcentaje.equals(primero.get(i).porcentaje)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ===== Cortes =====

    private void pintarCortes() {
        listaCortes.removeAllViews();
        for (int i = 0; i < cortes.size(); i++) {
            final int cual = i;
            listaCortes.addView(inflarFila(listaCortes, getString(R.string.forma_corte_numero, i + 1),
                    cortes.get(i), texto -> cortes.set(cual, texto), () -> quitarCorte(cual)));
        }
        etiquetaIgual.setText(getString(R.string.forma_igual_en_cortes, cortes.size()));
        llenarChipsDeCorte();
    }

    private void agregarCorte() {
        cortes.add("");
        tiposPorCorte.add(copiar(tiposPorCorte.get(igualEnCortes ? 0 : corteAConfigurar)));
        pintarCortes();
        revisarTotales();
    }

    /** Siempre queda al menos un corte. */
    private void quitarCorte(int cual) {
        if (cortes.size() <= 1) {
            return;
        }
        cortes.remove(cual);
        tiposPorCorte.remove(cual);
        corteAConfigurar = Math.min(corteAConfigurar, cortes.size() - 1);
        pintarCortes();
        pintarTipos();
        revisarTotales();
    }

    private void llenarChipsDeCorte() {
        chipsCorte.removeAllViews();
        for (int i = 0; i < cortes.size(); i++) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(getString(R.string.forma_corte_numero, i + 1));
            chip.setCheckable(true);
            chip.setChecked(i == corteAConfigurar);
            final int cual = i;
            chip.setOnCheckedChangeListener((boton, marcado) -> {
                if (marcado) {
                    corteAConfigurar = cual;
                    pintarTipos();
                    revisarTotales();
                }
            });
            chipsCorte.addView(chip);
        }
    }

    // ===== Tipos de nota =====

    private List<Tipo> tiposVisibles() {
        return tiposPorCorte.get(igualEnCortes ? 0 : corteAConfigurar);
    }

    private void pintarTipos() {
        listaTipos.removeAllViews();
        List<Tipo> tipos = tiposVisibles();
        for (int i = 0; i < tipos.size(); i++) {
            final int cual = i;
            Tipo tipo = tipos.get(i);
            listaTipos.addView(inflarFila(listaTipos, tipo.nombre, tipo.porcentaje,
                    texto -> cambiarPorcentajeDeTipo(cual, texto), () -> quitarTipo(cual)));
        }
    }

    /** Con "Igual en los cortes", lo que se cambia en uno se cambia en todos. */
    private void cambiarPorcentajeDeTipo(int cual, String texto) {
        if (igualEnCortes) {
            for (List<Tipo> tipos : tiposPorCorte) {
                tipos.get(cual).porcentaje = texto;
            }
        } else {
            tiposVisibles().get(cual).porcentaje = texto;
        }
    }

    private void quitarTipo(int cual) {
        if (igualEnCortes) {
            for (List<Tipo> tipos : tiposPorCorte) {
                tipos.remove(cual);
            }
        } else {
            tiposVisibles().remove(cual);
        }
        pintarTipos();
        revisarTotales();
    }

    /** El nombre del tipo nuevo se pide aparte: la fila solo deja escribir el porcentaje. */
    private void pedirNombreDeTipo() {
        View contenido = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialogo_nombre_tipo, null, false);
        TextInputEditText campo = contenido.findViewById(R.id.nombreTipo);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.forma_tipo_nuevo_titulo)
                .setView(contenido)
                .setNegativeButton(R.string.general_cancelar, null)
                .setPositiveButton(R.string.general_agregar, (dialogo, boton) -> {
                    String nombre = campo.getText() == null ? "" : campo.getText().toString().trim();
                    if (!nombre.isEmpty()) {
                        agregarTipo(nombre);
                    }
                })
                .show();
    }

    private void agregarTipo(String nombre) {
        // El id no sale del nombre: "Parcial 2" no puede volverse el parcial de una sola nota
        Tipo nuevo = new Tipo("tipo" + System.currentTimeMillis(), nombre, "", true);
        if (igualEnCortes) {
            for (List<Tipo> tipos : tiposPorCorte) {
                tipos.add(nuevo.copia());
            }
        } else {
            tiposVisibles().add(nuevo);
        }
        pintarTipos();
        revisarTotales();
    }

    private static List<Tipo> copiar(List<Tipo> tipos) {
        List<Tipo> copia = new ArrayList<>();
        for (Tipo tipo : tipos) {
            copia.add(tipo.copia());
        }
        return copia;
    }

    // ===== Filas =====

    private View inflarFila(ViewGroup padre, String nombre, String porcentaje,
                            Consumer<String> alCambiar, Runnable alQuitar) {
        View fila = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_porcentaje, padre, false);

        ((TextView) fila.findViewById(R.id.nombre)).setText(nombre);

        TextInputEditText campo = fila.findViewById(R.id.porcentaje);
        // Todas las filas comparten el id: sin esto Android les pondría el mismo texto al volver
        campo.setSaveEnabled(false);
        campo.setText(porcentaje);
        campo.addTextChangedListener(alEscribir(texto -> {
            alCambiar.accept(texto);
            revisarTotales();
        }));
        fila.findViewById(R.id.botonQuitar).setOnClickListener(v -> alQuitar.run());
        return fila;
    }

    private static TextWatcher alEscribir(Consumer<String> alCambiar) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                alCambiar.accept(texto.toString().trim());
            }
        };
    }

    // ===== Totales y guardar =====

    /** RF-08: no se puede guardar si algún total no da 100 %. */
    private void revisarTotales() {
        double sumaCortes = 0;
        for (String porcentaje : cortes) {
            sumaCortes += numero(porcentaje);
        }
        boolean tiposCuadran = true;
        for (List<Tipo> tipos : tiposPorCorte) {
            tiposCuadran &= esCien(sumaDe(tipos));
        }

        pintarTotal(totalCortes, sumaCortes);
        pintarTotal(totalTipos, sumaDe(tiposVisibles()));

        boolean cuadra = esCien(sumaCortes) && tiposCuadran;
        errorSuma.setVisibility(cuadra ? View.GONE : View.VISIBLE);
        botonGuardar.setEnabled(cuadra);
    }

    private static double sumaDe(List<Tipo> tipos) {
        double suma = 0;
        for (Tipo tipo : tipos) {
            suma += numero(tipo.porcentaje);
        }
        return suma;
    }

    private void pintarTotal(TextView vista, double suma) {
        vista.setText(getString(R.string.forma_total,
                getString(R.string.general_porcentaje, Formato.porcentaje(suma))));
        vista.setTextColor(ContextCompat.getColor(requireContext(),
                esCien(suma) ? R.color.aprobado : R.color.reprobado));
    }

    private static boolean esCien(double suma) {
        return Math.abs(suma - 100) < 0.001;
    }

    /** Un campo vacío o a medio escribir vale 0. */
    private static double numero(String texto) {
        if (texto == null || texto.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(texto.replace(',', '.'));
        } catch (NumberFormatException sinNumero) {
            return 0;
        }
    }

    private void guardar(View vista) {
        double pasa = numero(notaParaPasar);
        TextInputLayout campoPasar = vista.findViewById(R.id.campoNotaParaPasar);
        boolean pasaValida = pasa > 0 && pasa <= 5;
        campoPasar.setError(pasaValida ? null : getString(R.string.forma_error_nota_pasar));
        if (!pasaValida) {
            return;
        }

        List<Corte> forma = new ArrayList<>();
        for (int i = 0; i < cortes.size(); i++) {
            List<Componente> componentes = new ArrayList<>();
            for (Tipo tipo : tiposPorCorte.get(i)) {
                componentes.add(new Componente(tipo.id, tipo.nombre, numero(tipo.porcentaje), tipo.promedioIgual));
            }
            forma.add(new Corte(i + 1, numero(cortes.get(i)), componentes));
        }
        if (materia != null) {
            Repositorio.get().guardarFormaDeMateria(materia.id, forma, pasa);
        } else {
            Repositorio.get().guardarFormaDeCalificar(forma, pasa);
        }
        volver();
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
