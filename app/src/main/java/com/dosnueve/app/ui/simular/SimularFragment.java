package com.dosnueve.app.ui.simular;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.util.Formato;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 11 Simular (doc 03, RF-23 y HU-05).
 *
 * <p>El resultado queda fijo arriba y abajo va una tarjeta por corte: lo registrado como
 * fila con candado y lo que falta con un deslizador. Cada parte dice cuánto vale de la
 * materia, para saber qué conviene mover. Nada de esto se guarda: la definitiva simulada
 * se recalcula al instante con los pesos reales y "Reiniciar" vuelve a lo que necesita.
 */
public class SimularFragment extends Fragment {

    private static final String ARG_MATERIA = "materiaId";
    private static final double MARGEN = 1e-9;
    private static final Locale ESPANOL = new Locale("es", "CO");

    /** Algo que aporta a la definitiva: se puede simular o ya tiene nota y queda bloqueado. */
    private static class Parte {

        final String nombre;
        /** Cuánto aporta a la definitiva: %corte × %componente / 10000. */
        final double peso;
        final double valorInicial;
        final boolean bloqueado;

        Parte(String nombre, double peso, double valorInicial, boolean bloqueado) {
            this.nombre = nombre;
            this.peso = peso;
            this.valorInicial = valorInicial;
            this.bloqueado = bloqueado;
        }
    }

    /** Un corte con sus partes. Si ya está evaluado, va completo con su nota. */
    private static class CorteSimulado {

        final int numero;
        final double porcentaje;
        @Nullable
        final Double nota;
        final List<Parte> partes = new ArrayList<>();

        CorteSimulado(int numero, double porcentaje, @Nullable Double nota) {
            this.numero = numero;
            this.porcentaje = porcentaje;
            this.nota = nota;
        }
    }

    private final List<Parte> partes = new ArrayList<>();
    private final List<Double> valores = new ArrayList<>();
    /** Lo que hace "Reiniciar" en cada deslizador. */
    private final List<Runnable> reinicios = new ArrayList<>();
    private Materia materia;

    private TextView definitivaSimulada;
    private TextView veredicto;
    private View botonReiniciar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_simular, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        definitivaSimulada = vista.findViewById(R.id.definitivaSimulada);
        veredicto = vista.findViewById(R.id.veredicto);
        botonReiniciar = vista.findViewById(R.id.botonReiniciar);
        botonReiniciar.setOnClickListener(v -> reiniciar());

        List<Materia> materias = new ArrayList<>(Repositorio.get().materias());
        if (materias.isEmpty()) {
            definitivaSimulada.setText(Formato.PENDIENTE);
            veredicto.setText(R.string.simular_sin_materias);
            veredicto.setBackground(null);
            return;
        }

        long pedida = getArguments() == null ? 0 : getArguments().getLong(ARG_MATERIA, 0);
        int elegida = 0;
        for (int i = 0; i < materias.size(); i++) {
            if (materias.get(i).id.equals(String.valueOf(pedida))) {
                elegida = i;
            }
        }

        ChipGroup chipsMateria = vista.findViewById(R.id.chipsMateria);
        for (int i = 0; i < materias.size(); i++) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(materias.get(i).nombre);
            chip.setCheckable(true);
            chip.setChecked(i == elegida);
            final int cual = i;
            chip.setOnCheckedChangeListener((boton, marcado) -> {
                if (marcado) {
                    cargar(vista, materias.get(cual));
                }
            });
            chipsMateria.addView(chip);
        }
        cargar(vista, materias.get(elegida));
    }

    private void cargar(View vista, Materia elegida) {
        materia = elegida;
        partes.clear();
        valores.clear();
        reinicios.clear();

        LinearLayout lista = vista.findViewById(R.id.listaCortes);
        lista.removeAllViews();
        for (CorteSimulado corte : cortesDe(elegida)) {
            lista.addView(construirCorte(lista, corte));
        }
        botonReiniciar.setVisibility(View.INVISIBLE);
        recalcular();
    }

    /**
     * Los cortes evaluados van completos y bloqueados. En los que faltan, cada componente:
     * bloqueado si ya tiene nota, para simular si no. En "Con porcentajes" con parte
     * registrada, lo registrado se bloquea y lo que falta se simula aparte.
     *
     * <p>Lo que falta arranca en lo que necesita, y la autoevaluación en 4.5 (RN-13).
     */
    private List<CorteSimulado> cortesDe(Materia elegida) {
        List<Nota> notas = Repositorio.get().notas(elegida.id);
        Double necesitas = CalculadoraNotas.resumen(elegida, notas).necesitas;
        double porDefecto = necesitas == null ? elegida.notaParaPasar
                : Math.max(0, Math.min(CalculadoraNotas.NOTA_MAXIMA, necesitas));

        List<CorteSimulado> cortes = new ArrayList<>();
        for (Corte corte : elegida.cortes) {
            CorteSimulado simulado = new CorteSimulado(corte.numero, corte.porcentaje,
                    CalculadoraNotas.notaCorte(corte, notas));
            cortes.add(simulado);
            if (simulado.nota != null) {
                continue;
            }
            for (Componente componente : corte.componentes) {
                double peso = corte.porcentaje * componente.porcentaje / 10000;
                Double suNota = CalculadoraNotas.notaComponente(corte.numero, componente, notas);
                double falta = CalculadoraNotas.porcentajeSinNotas(corte.numero, componente, notas);
                if (suNota == null) {
                    double inicial = CalculadoraNotas.ID_AUTOEVALUACION.equals(componente.id)
                            ? CalculadoraNotas.AUTOEVALUACION_SUPUESTA : porDefecto;
                    simulado.partes.add(new Parte(componente.nombre, peso, inicial, false));
                } else if (falta <= MARGEN) {
                    simulado.partes.add(new Parte(componente.nombre, peso, suNota, true));
                } else {
                    double cubierto = componente.porcentaje - falta;
                    simulado.partes.add(new Parte(componente.nombre,
                            corte.porcentaje * cubierto / 10000, suNota, true));
                    simulado.partes.add(new Parte(getString(R.string.simular_parte_que_falta,
                            componente.nombre.toLowerCase(ESPANOL)),
                            corte.porcentaje * falta / 10000, porDefecto, false));
                }
            }
        }
        return cortes;
    }

    private View construirCorte(ViewGroup padre, CorteSimulado corte) {
        View tarjeta = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_corte_simulado, padre, false);
        ((TextView) tarjeta.findViewById(R.id.nombre))
                .setText(getString(R.string.forma_corte_numero, corte.numero));
        ((TextView) tarjeta.findViewById(R.id.vale))
                .setText(getString(R.string.simular_vale_corte, Formato.porcentaje(corte.porcentaje)));

        LinearLayout contenedor = tarjeta.findViewById(R.id.partes);
        if (corte.nota != null) {
            // Un corte ya evaluado no se simula: una sola línea con su nota
            registrar(new Parte(getString(R.string.forma_corte_numero, corte.numero),
                    corte.porcentaje / 100, corte.nota, true));
            tarjeta.findViewById(R.id.candado).setVisibility(View.VISIBLE);
            TextView nota = tarjeta.findViewById(R.id.nota);
            nota.setVisibility(View.VISIBLE);
            nota.setText(Formato.nota(corte.nota));
            contenedor.setVisibility(View.GONE);
            return tarjeta;
        }
        for (Parte parte : corte.partes) {
            contenedor.addView(parte.bloqueado
                    ? construirFija(contenedor, parte)
                    : construirDeslizador(contenedor, parte));
        }
        return tarjeta;
    }

    /** Suma la parte al cálculo y devuelve su posición en {@link #valores}. */
    private int registrar(Parte parte) {
        partes.add(parte);
        valores.add(parte.valorInicial);
        return partes.size() - 1;
    }

    /** "20 %": cuánto vale esa parte de la materia completa. */
    private String pesoEnLaMateria(Parte parte) {
        return getString(R.string.general_porcentaje, Formato.porcentaje(parte.peso * 100));
    }

    private View construirFija(ViewGroup padre, Parte parte) {
        registrar(parte);
        View fila = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_simulado_fijo, padre, false);
        ((TextView) fila.findViewById(R.id.nombre)).setText(parte.nombre);
        ((TextView) fila.findViewById(R.id.peso)).setText(pesoEnLaMateria(parte));
        ((TextView) fila.findViewById(R.id.valor)).setText(Formato.nota(parte.valorInicial));
        return fila;
    }

    private View construirDeslizador(ViewGroup padre, Parte parte) {
        int indice = registrar(parte);
        View bloque = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_deslizador, padre, false);

        TextView valor = bloque.findViewById(R.id.valor);
        Slider deslizador = bloque.findViewById(R.id.deslizador);
        ((TextView) bloque.findViewById(R.id.nombre)).setText(parte.nombre);
        ((TextView) bloque.findViewById(R.id.peso)).setText(pesoEnLaMateria(parte));
        deslizador.setContentDescription(parte.nombre);

        Runnable alInicio = () -> {
            valores.set(indice, parte.valorInicial);
            valor.setText(Formato.nota(parte.valorInicial));
            // El deslizador va de 0.1 en 0.1: no acepta un 3.53 calculado
            deslizador.setValue(Math.round(parte.valorInicial * 10) / 10f);
        };
        alInicio.run();
        reinicios.add(alInicio);

        deslizador.addOnChangeListener((control, nuevoValor, delUsuario) -> {
            if (!delUsuario) {
                return;
            }
            valores.set(indice, (double) nuevoValor);
            valor.setText(Formato.nota((double) nuevoValor));
            botonReiniciar.setVisibility(View.VISIBLE);
            recalcular();
        });
        return bloque;
    }

    private void reiniciar() {
        for (Runnable alInicio : reinicios) {
            alInicio.run();
        }
        botonReiniciar.setVisibility(View.INVISIBLE);
        recalcular();
    }

    /** definitiva = Σ (nota × peso) · doc 02 · 3.2 y 3.6. */
    private void recalcular() {
        double definitiva = 0;
        for (int i = 0; i < partes.size(); i++) {
            definitiva += valores.get(i) * partes.get(i).peso;
        }

        // Se compara sin redondear: 2.99 no pasa (RN-08)
        boolean pasa = CalculadoraNotas.aprueba(definitiva, materia.notaParaPasar);

        definitivaSimulada.setText(
                Formato.notaSinFalsasEsperanzas(definitiva, materia.notaParaPasar));
        veredicto.setText(pasa ? R.string.simular_pasarias : R.string.simular_no_pasarias);
        veredicto.setBackgroundResource(pasa ? R.drawable.fondo_nota_aprobada : R.drawable.fondo_nota_riesgo);
    }
}
