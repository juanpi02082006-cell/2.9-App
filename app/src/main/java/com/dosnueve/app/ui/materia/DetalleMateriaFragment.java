package com.dosnueve.app.ui.materia;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.util.Formato;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * 08 Detalle de materia (doc 03, RF-21 y HU-01).
 *
 * <p>Todo sale de {@link CalculadoraNotas} con la materia y sus notas del
 * {@link Repositorio}: lo que lleva, el % evaluado, cuánto necesita en lo que falta y la
 * nota de cada corte.
 */
public class DetalleMateriaFragment extends Fragment {

    private static final String ARG_MATERIA = "materiaId";
    private static final double ESCALA_MAXIMA = 5.0;

    private long materiaId;
    private CorteAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_detalle_materia, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        materiaId = requireArguments().getLong(ARG_MATERIA);

        RecyclerView lista = vista.findViewById(R.id.listaCortes);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        adaptador = new CorteAdapter(this::irAlDetalleDelCorte);
        lista.setAdapter(adaptador);

        vista.findViewById(R.id.botonVolver).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        vista.findViewById(R.id.botonEditar).setOnClickListener(v ->
                irA(R.id.editarMateriaFragment));
        vista.findViewById(R.id.botonSimular).setOnClickListener(v ->
                irA(R.id.simularFragment));
        vista.findViewById(R.id.botonRegistrarNota).setOnClickListener(v ->
                irA(R.id.registrarNotaFragment));

        Repositorio.get().cambios().observe(getViewLifecycleOwner(), cambio -> pintar(vista));
    }

    private void pintar(View vista) {
        Repositorio datos = Repositorio.get();
        Materia materia = datos.materia(String.valueOf(materiaId));
        if (materia == null) {
            // La borraron desde Editar materia
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        List<Nota> notas = datos.notas(materia.id);
        CalculadoraNotas.Resumen resumen = CalculadoraNotas.resumen(materia, notas);

        pintarEncabezado(vista, materia, resumen);
        pintarCuantoNecesita(vista, materia, notas, resumen);
        adaptador.mostrar(cortes(materia, notas, resumen));
        pintarNotaAMano(vista, materia, notas, resumen);
    }

    private void pintarEncabezado(View vista, Materia materia, CalculadoraNotas.Resumen resumen) {
        TextView nombre = vista.findViewById(R.id.nombreMateria);
        TextView subtitulo = vista.findViewById(R.id.subtituloMateria);
        TextView numero = vista.findViewById(R.id.llevas);
        TextView evaluado = vista.findViewById(R.id.porcentajeEvaluado);

        nombre.setText(materia.nombre);
        subtitulo.setText(getString(R.string.detalle_creditos, materia.creditos));

        numero.setText(Formato.notaSinFalsasEsperanzas(resumen.llevas, materia.notaParaPasar));
        // Como letra van los tonos oscuros: el coral y el verde vivos no llegan a 3:1 sobre el fondo
        int color = resumen.llevas == null ? R.color.texto_secundario
                : resumen.enRiesgo ? R.color.riesgo : R.color.aprobado;
        numero.setTextColor(ContextCompat.getColor(requireContext(), color));

        double porcentajeEvaluado = resumen.porcentajeEvaluado;
        CircularProgressIndicator anillo = vista.findViewById(R.id.anilloEvaluado);
        anillo.setProgress((int) Math.round(porcentajeEvaluado));
        evaluado.setText(getString(R.string.detalle_evaluado_anillo,
                Formato.porcentaje(porcentajeEvaluado)));
        vista.findViewById(R.id.bloqueEvaluado).setContentDescription(
                getString(R.string.detalle_evaluado, Formato.porcentaje(porcentajeEvaluado)));
    }

    /**
     * Tarjeta con "cuánto necesitas" (doc 02 · 3.4). Si no alcanza o ya pasó, se dice con
     * palabras en vez de mostrar un número imposible. Con todos los cortes, la definitiva.
     */
    private void pintarCuantoNecesita(View vista, Materia materia, List<Nota> notas,
                                      CalculadoraNotas.Resumen resumen) {
        TextView etiqueta = vista.findViewById(R.id.etiquetaNecesitas);
        TextView numero = vista.findViewById(R.id.necesitas);
        TextView detalle = vista.findViewById(R.id.necesitasDonde);

        if (resumen.necesitas == null) {
            etiqueta.setText(R.string.detalle_definitiva);
            numero.setVisibility(View.VISIBLE);
            numero.setText(Formato.notaSinFalsasEsperanzas(resumen.definitiva, materia.notaParaPasar));
            detalle.setText(resumen.aprobada() ? R.string.detalle_aprobaste : R.string.detalle_no_aprobaste);
            return;
        }
        if (resumen.yaNoAlcanza()) {
            etiqueta.setText(R.string.detalle_ya_no_alcanza);
            numero.setVisibility(View.GONE);
            detalle.setText(R.string.detalle_ya_no_alcanza_detalle);
            return;
        }
        if (resumen.yaPaso()) {
            etiqueta.setText(R.string.detalle_ya_pasaste);
            numero.setVisibility(View.GONE);
            detalle.setText(R.string.detalle_ya_pasaste_detalle);
            return;
        }
        etiqueta.setText(R.string.detalle_necesitas_etiqueta);
        numero.setVisibility(View.VISIBLE);
        numero.setText(Formato.nota(resumen.necesitas));

        List<Corte> faltan = new ArrayList<>();
        for (Corte corte : materia.cortes) {
            if (!CalculadoraNotas.corteEvaluado(corte, notas)) {
                faltan.add(corte);
            }
        }
        detalle.setText(faltan.size() == 1
                ? getString(R.string.detalle_necesitas_donde,
                        getString(R.string.forma_corte_numero, faltan.get(0).numero))
                : getString(R.string.detalle_necesitas_cada_corte));
    }

    private List<CorteAdapter.CorteEnPantalla> cortes(Materia materia, List<Nota> notas,
                                                      CalculadoraNotas.Resumen resumen) {
        List<CorteAdapter.CorteEnPantalla> cortes = new ArrayList<>();
        for (Corte corte : materia.cortes) {
            Double nota = CalculadoraNotas.notaCorte(corte, notas);
            // La meta solo va en los cortes que faltan (doc 02 · 3.4)
            Double meta = nota == null ? resumen.necesitas : null;

            String[] nombres = new String[corte.componentes.size()];
            Double[] notasComponentes = new Double[corte.componentes.size()];
            for (int i = 0; i < nombres.length; i++) {
                Componente componente = corte.componentes.get(i);
                nombres[i] = componente.nombre + " · "
                        + getString(R.string.general_porcentaje, Formato.porcentaje(componente.porcentaje));
                notasComponentes[i] = CalculadoraNotas.notaComponente(corte.numero, componente, notas);
            }
            cortes.add(new CorteAdapter.CorteEnPantalla(corte.numero,
                    getString(R.string.forma_corte_numero, corte.numero), corte.porcentaje, nota, meta,
                    nombres, notasComponentes));
        }
        return cortes;
    }

    /**
     * La única nota a mano de la pantalla: lo que necesita en el parcial del corte en curso
     * para llegar a su meta (doc 02 · 3.5). Solo si el parcial todavía no tiene nota.
     */
    private void pintarNotaAMano(View vista, Materia materia, List<Nota> notas,
                                 CalculadoraNotas.Resumen resumen) {
        View bloque = vista.findViewById(R.id.notaAMano);
        TextView texto = vista.findViewById(R.id.textoAMano);

        Corte enCurso = CalculadoraNotas.corteEnCurso(materia, notas);
        CalculadoraNotas.NecesarioEnCorte falta = enCurso == null || resumen.necesitas == null
                ? null
                : CalculadoraNotas.necesarioEnCorte(enCurso, notas, resumen.necesitas);
        boolean faltaElParcial = false;
        if (falta != null) {
            for (CalculadoraNotas.Pendiente pendiente : falta.enQue) {
                if (CalculadoraNotas.ID_PARCIAL.equals(pendiente.componenteId)) {
                    faltaElParcial = true;
                }
            }
        }
        if (!faltaElParcial || falta.nota <= 0 || falta.nota > ESCALA_MAXIMA) {
            bloque.setVisibility(View.GONE);
            return;
        }
        bloque.setVisibility(View.VISIBLE);
        texto.setText(getString(R.string.detalle_a_mano, Formato.nota(falta.nota)));
    }

    private void irAlDetalleDelCorte(long numeroCorte) {
        Bundle argumentos = new Bundle();
        argumentos.putLong(ARG_MATERIA, materiaId);
        argumentos.putLong("corteId", numeroCorte);
        NavHostFragment.findNavController(this)
                .navigate(R.id.detalleCorteFragment, argumentos);
    }

    private void irA(int destino) {
        Bundle argumentos = new Bundle();
        argumentos.putLong(ARG_MATERIA, materiaId);
        NavHostFragment.findNavController(this).navigate(destino, argumentos);
    }
}
