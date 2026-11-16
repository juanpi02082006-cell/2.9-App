package com.dosnueve.app.ui.corte;

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
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.ui.TextoPendientes;
import com.dosnueve.app.ui.nota.EditarNotaBottomSheet;
import com.dosnueve.app.util.Formato;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 09 Detalle de corte · 17 Con porcentajes (doc 03, RF-16, RF-17 y RF-22).
 *
 * <p>Una sección por componente. Un componente puede tener varias notas; por defecto
 * valen igual y se puede cambiar a "Con porcentajes", donde cada nota lleva lo que vale
 * en % del corte (como lo da el profesor: 15 + 10 + 5 = 30) y no puede pasar del % del
 * componente (RN-04). Los números salen de {@link CalculadoraNotas} con las notas del
 * {@link Repositorio}.
 */
public class DetalleCorteFragment extends Fragment {

    private static final double ESCALA_MAXIMA = 5.0;

    private String materiaId;
    private int numeroCorte;
    private View raiz;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_detalle_corte, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        raiz = vista;
        materiaId = String.valueOf(requireArguments().getLong("materiaId"));
        numeroCorte = (int) requireArguments().getLong("corteId");

        vista.findViewById(R.id.botonVolver).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        // Una nota nueva, editada o borrada desde la hoja vuelve a pintar todo
        Repositorio.get().cambios().observe(getViewLifecycleOwner(), cambio -> pintarTodo());
    }

    private void pintarTodo() {
        Materia materia = Repositorio.get().materia(materiaId);
        Corte corte = Repositorio.corte(materia, numeroCorte);
        if (corte == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        TextView titulo = raiz.findViewById(R.id.titulo);
        TextView vale = raiz.findViewById(R.id.vale);
        titulo.setText(getString(R.string.corte_titulo, materia.nombre,
                getString(R.string.forma_corte_numero, numeroCorte)));
        vale.setText(getString(R.string.corte_vale, Formato.porcentaje(corte.porcentaje)));

        pintarMetaYParcial();
        pintarComponentes();
    }

    // ===== Datos =====

    private Materia materia() {
        return Repositorio.get().materia(materiaId);
    }

    private Corte corte() {
        return Repositorio.corte(materia(), numeroCorte);
    }

    private List<Nota> notas() {
        return Repositorio.get().notas(materiaId);
    }

    /** La meta del corte es lo que la materia necesita en lo que falta (doc 02 · 3.5). */
    private double meta() {
        Materia materia = materia();
        Double necesitas = CalculadoraNotas.resumen(materia, notas()).necesitas;
        return necesitas == null ? materia.notaParaPasar : necesitas;
    }

    private List<Nota> notasDe(Componente componente) {
        List<Nota> lista = new ArrayList<>();
        for (Nota nota : notas()) {
            if (nota.corte == numeroCorte && Objects.equals(nota.componenteId, componente.id)) {
                lista.add(nota);
            }
        }
        return lista;
    }

    /**
     * Meta del corte y nota necesaria en lo que falta (doc 02 · 3.5), con la barra de 0 a 5.
     * Si la autoevaluación no tiene nota se supone 4.5 y la pantalla lo dice (RN-13).
     */
    private void pintarMetaYParcial() {
        Corte corte = corte();
        List<Nota> notas = notas();
        double meta = meta();

        TextView chipMeta = raiz.findViewById(R.id.chipMeta);
        TextView necesitas = raiz.findViewById(R.id.necesitas);
        chipMeta.setText(getString(R.string.corte_meta, Formato.nota(meta)));
        chipMeta.setVisibility(CalculadoraNotas.corteEvaluado(corte, notas) ? View.GONE : View.VISIBLE);

        TextView etiqueta = raiz.findViewById(R.id.etiquetaNecesitas);
        CalculadoraNotas.NecesarioEnCorte falta = CalculadoraNotas.necesarioEnCorte(corte, notas, meta);

        // Sin nada pendiente ya no hay "necesitas": se muestra cómo quedó el corte
        double valor;
        if (falta == null) {
            Double notaCorte = CalculadoraNotas.notaCorte(corte, notas);
            valor = notaCorte == null ? 0 : notaCorte;
            etiqueta.setText(R.string.corte_queda_en);
        } else if (falta.nota <= 0) {
            // Con lo que tiene (y el 4.5 supuesto, si aplica) ya llega a la meta
            valor = 0;
            etiqueta.setText(R.string.corte_ya_llegas);
        } else {
            // "Necesitas en el parcial y el 10 % que falta de trabajos": sin suponer nada
            valor = falta.nota;
            etiqueta.setText(getString(R.string.corte_necesitas_en,
                    TextoPendientes.describir(requireContext(), falta.enQue)));
        }
        necesitas.setText(Formato.nota(valor));
        ajustarBarraDeEscala(valor);

        raiz.findViewById(R.id.suponiendoAuto).setVisibility(
                falta != null && falta.autoevaluacionSupuesta ? View.VISIBLE : View.GONE);
    }

    /** La barra de 0 a 5 se reparte con pesos (doc 04 · 6). */
    private void ajustarBarraDeEscala(double valor) {
        View llena = raiz.findViewById(R.id.barraLlena);
        View vacia = raiz.findViewById(R.id.barraVacia);

        float fraccion = (float) Math.max(0, Math.min(valor, ESCALA_MAXIMA)) / (float) ESCALA_MAXIMA;
        ponerPeso(llena, fraccion);
        ponerPeso(vacia, 1 - fraccion);
    }

    private void ponerPeso(View vista, float peso) {
        LinearLayout.LayoutParams medidas = (LinearLayout.LayoutParams) vista.getLayoutParams();
        medidas.weight = peso;
        vista.setLayoutParams(medidas);
    }

    // ===== Secciones =====

    private void pintarComponentes() {
        LinearLayout contenedor = raiz.findViewById(R.id.listaComponentes);
        contenedor.removeAllViews();

        for (Componente componente : corte().componentes) {
            contenedor.addView(construirSeccion(contenedor, componente));
        }
    }

    private View construirSeccion(ViewGroup padre, Componente componente) {
        View seccion = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_componente, padre, false);

        TextView nombre = seccion.findViewById(R.id.nombre);
        TextView porcentaje = seccion.findViewById(R.id.porcentaje);
        View sinNotas = seccion.findViewById(R.id.sinNotas);
        LinearLayout listaNotas = seccion.findViewById(R.id.listaNotas);
        MaterialButton botonModo = seccion.findViewById(R.id.botonModoPromedio);
        List<Nota> susNotas = notasDe(componente);

        nombre.setText(componente.nombre);
        porcentaje.setText(getString(R.string.corte_porcentaje_del_corte,
                Formato.porcentaje(componente.porcentaje)));
        sinNotas.setVisibility(susNotas.isEmpty() ? View.VISIBLE : View.GONE);

        for (Nota nota : susNotas) {
            listaNotas.addView(construirFilaDeNota(listaNotas, seccion, componente, nota));
        }
        refrescarSeccion(seccion, componente);

        // El enlace alterna entre los dos modos de promedio (RF-17, pantalla 17). El parcial
        // y la autoevaluación no lo tienen: son una nota cada uno
        boolean unaSolaNota = CalculadoraNotas.esUnaSolaNota(componente.id);
        botonModo.setText(componente.promedioIgual
                ? R.string.corte_con_porcentajes
                : R.string.corte_todas_igual);
        botonModo.setVisibility(!unaSolaNota && susNotas.size() > 1 ? View.VISIBLE : View.GONE);
        botonModo.setOnClickListener(v -> Repositorio.get()
                .cambiarModo(materiaId, numeroCorte, componente.id, !componente.promedioIgual));

        // Con su nota, "+ Nota" sobra en el parcial y la autoevaluación: se edita tocándola
        View agregar = seccion.findViewById(R.id.botonAgregarNota);
        agregar.setVisibility(unaSolaNota && !susNotas.isEmpty() ? View.GONE : View.VISIBLE);
        agregar.setOnClickListener(v ->
                EditarNotaBottomSheet.nueva(materiaId, numeroCorte, componente.id)
                        .show(getParentFragmentManager(), EditarNotaBottomSheet.ETIQUETA));

        return seccion;
    }

    /**
     * Promedio del componente (pastilla verde o coral) y, en "Con porcentajes", el total
     * de los pesos: "Llevas 20 de 30 %" en azul, en rojo si se pasa (RN-04).
     */
    private void refrescarSeccion(View seccion, Componente componente) {
        TextView promedio = seccion.findViewById(R.id.promedio);
        TextView total = seccion.findViewById(R.id.totalPesos);
        List<Nota> notas = notas();

        Double valor = CalculadoraNotas.notaComponente(numeroCorte, componente, notas);
        promedio.setText(Formato.nota(valor));
        if (valor != null) {
            promedio.setBackgroundResource(CalculadoraNotas.aprueba(valor, materia().notaParaPasar)
                    ? R.drawable.fondo_nota_aprobada
                    : R.drawable.fondo_nota_riesgo);
        } else {
            promedio.setBackground(null);
        }

        boolean conPesos = !componente.promedioIgual && !notasDe(componente).isEmpty();
        total.setVisibility(conPesos ? View.VISIBLE : View.GONE);
        if (conPesos) {
            boolean sePasa = CalculadoraNotas.pesosSePasan(numeroCorte, componente, notas);
            String registrado = Formato.porcentaje(
                    CalculadoraNotas.pesoRegistrado(numeroCorte, componente, notas));
            String delComponente = Formato.porcentaje(componente.porcentaje);
            total.setText(getString(sePasa ? R.string.corte_total_pesos_pasa : R.string.corte_total_pesos,
                    registrado, delComponente));
            total.setTextColor(ContextCompat.getColor(requireContext(),
                    sePasa ? R.color.reprobado : R.color.azul_oscuro));
        }
    }

    private View construirFilaDeNota(ViewGroup padre, View seccion, Componente componente, Nota nota) {
        View fila = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_nota, padre, false);

        TextView nombre = fila.findViewById(R.id.nombre);
        TextView fecha = fila.findViewById(R.id.fecha);
        TextView valor = fila.findViewById(R.id.valor);
        View campoPeso = fila.findViewById(R.id.campoPeso);
        EditText peso = fila.findViewById(R.id.peso);

        nombre.setText(nota.nombre == null || nota.nombre.isEmpty() ? componente.nombre : nota.nombre);
        fecha.setText(Formato.fecha(nota.fecha));
        valor.setText(Formato.nota(nota.valor));

        // El peso solo se muestra en modo "Con porcentajes" (pantalla 17)
        campoPeso.setVisibility(componente.promedioIgual ? View.GONE : View.VISIBLE);
        // Todas las filas comparten el id "peso": sin esto, Android les devolvería a
        // todas el mismo texto guardado al recrear la vista
        peso.setSaveEnabled(false);
        if (nota.peso != null) {
            peso.setText(Formato.porcentaje(nota.peso));
        }
        peso.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                Repositorio.get().cambiarPeso(materiaId, nota.id, leerPeso(texto));
                refrescarSeccion(seccion, componente);
                pintarMetaYParcial();
            }
        });

        fila.setOnClickListener(v -> EditarNotaBottomSheet.editar(materiaId, nota.id)
                .show(getParentFragmentManager(), EditarNotaBottomSheet.ETIQUETA));

        return fila;
    }

    @Nullable
    private static Double leerPeso(Editable texto) {
        try {
            double valor = Double.parseDouble(texto.toString().trim());
            return valor > 0 ? valor : null;
        } catch (NumberFormatException sinNumero) {
            return null;
        }
    }
}
