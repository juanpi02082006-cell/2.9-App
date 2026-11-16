package com.dosnueve.app.ui.inicio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.util.Formato;

import java.util.ArrayList;
import java.util.List;

/** Pinta la planilla de materias del Inicio (pantalla 03, RF-19). */
public class PlanillaAdapter extends RecyclerView.Adapter<PlanillaAdapter.FilaVista> {

    /** Qué hacer cuando se toca una fila: ir al detalle de la materia (pantalla 08). */
    public interface AlTocarMateria {
        void alTocar(long materiaId);
    }

    private final List<FilaPlanilla> filas = new ArrayList<>();
    private final AlTocarMateria alTocarMateria;

    public PlanillaAdapter(AlTocarMateria alTocarMateria) {
        this.alTocarMateria = alTocarMateria;
    }

    public void mostrar(List<FilaPlanilla> nuevas) {
        filas.clear();
        filas.addAll(nuevas);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FilaVista onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_planilla, padre, false);
        return new FilaVista(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull FilaVista vista, int posicion) {
        boolean esLaUltima = posicion == filas.size() - 1;
        vista.pintar(filas.get(posicion), esLaUltima, alTocarMateria);
    }

    @Override
    public int getItemCount() {
        return filas.size();
    }

    /**
     * Casilla de la nota en una planilla: coral si va en riesgo o ya perdió, verde si va en
     * la nota para pasar o más, y sin casilla si todavía no es ni lo uno ni lo otro.
     */
    public static void pintarCasilla(TextView nota, boolean enRojo, boolean vaBien) {
        nota.setBackgroundResource(enRojo ? R.drawable.fondo_celda_riesgo
                : vaBien ? R.drawable.fondo_celda_aprobada : 0);
    }

    static class FilaVista extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView creditos;
        private final TextView[] cortes;
        private final TextView definitiva;
        private final View divisor;

        FilaVista(@NonNull View vista) {
            super(vista);
            nombre = vista.findViewById(R.id.nombre);
            creditos = vista.findViewById(R.id.creditos);
            cortes = new TextView[]{
                    vista.findViewById(R.id.corte1),
                    vista.findViewById(R.id.corte2),
                    vista.findViewById(R.id.corte3)
            };
            definitiva = vista.findViewById(R.id.definitiva);
            divisor = vista.findViewById(R.id.divisor);
        }

        void pintar(FilaPlanilla fila, boolean esLaUltima, AlTocarMateria alTocar) {
            nombre.setText(fila.nombre);
            creditos.setText(itemView.getResources()
                    .getString(R.string.detalle_creditos, fila.creditos));

            for (int i = 0; i < cortes.length; i++) {
                Double nota = i < fila.notasCortes.length ? fila.notasCortes[i] : null;
                cortes[i].setText(Formato.nota(nota));
                cortes[i].setTextColor(ContextCompat.getColor(itemView.getContext(),
                        nota == null ? R.color.texto_secundario : R.color.tinta));
            }

            definitiva.setText(Formato.notaSinFalsasEsperanzas(fila.llevas, fila.notaParaPasar));

            boolean vaBien = !fila.enRiesgo && fila.llevas != null
                    && CalculadoraNotas.aprueba(fila.llevas, fila.notaParaPasar);
            pintarCasilla(definitiva, fila.enRiesgo, vaBien);
            itemView.setContentDescription(fila.enRiesgo
                    ? fila.nombre + ". " + itemView.getContext().getString(R.string.planilla_en_riesgo)
                    : fila.nombre);

            divisor.setVisibility(esLaUltima ? View.GONE : View.VISIBLE);

            itemView.setOnClickListener(v -> alTocar.alTocar(fila.materiaId));
        }
    }
}
