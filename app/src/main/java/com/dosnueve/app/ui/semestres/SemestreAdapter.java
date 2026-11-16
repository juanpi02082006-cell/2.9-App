package com.dosnueve.app.ui.semestres;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.dosnueve.app.util.Formato;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** Lista de semestres de la pantalla 10. Cada fila lleva al detalle (23). */
public class SemestreAdapter extends RecyclerView.Adapter<SemestreAdapter.SemestreVista> {

    public interface AlTocarSemestre {
        void alTocar(long semestreId);
    }

    public static class SemestreEnPantalla {

        public final long id;
        public final String periodo;
        public final int materias;
        public final int creditos;
        public final Double promedio;
        public final boolean terminado;
        /** Solo del semestre en curso: en qué semana va y cuántas tiene. */
        public final Integer semanaActual;
        public final Integer semanasTotales;

        public SemestreEnPantalla(long id, String periodo, int materias, int creditos,
                                  Double promedio, boolean terminado) {
            this(id, periodo, materias, creditos, promedio, terminado, null, null);
        }

        public SemestreEnPantalla(long id, String periodo, int materias, int creditos,
                                  Double promedio, boolean terminado,
                                  Integer semanaActual, Integer semanasTotales) {
            this.id = id;
            this.periodo = periodo;
            this.materias = materias;
            this.creditos = creditos;
            this.promedio = promedio;
            this.terminado = terminado;
            this.semanaActual = semanaActual;
            this.semanasTotales = semanasTotales;
        }

        /** Cuánto va del semestre, de 0 a 100. {@code null} si no aplica. */
        Integer porcentajeAvance() {
            if (terminado || semanaActual == null || semanasTotales == null || semanasTotales <= 0) {
                return null;
            }
            int semana = Math.max(0, Math.min(semanaActual, semanasTotales));
            return Math.round(semana * 100f / semanasTotales);
        }
    }

    private final List<SemestreEnPantalla> semestres = new ArrayList<>();
    private final AlTocarSemestre alTocarSemestre;

    public SemestreAdapter(AlTocarSemestre alTocarSemestre) {
        this.alTocarSemestre = alTocarSemestre;
    }

    public void mostrar(List<SemestreEnPantalla> nuevos) {
        semestres.clear();
        semestres.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SemestreVista onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_semestre, padre, false);
        return new SemestreVista(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull SemestreVista vista, int posicion) {
        vista.pintar(semestres.get(posicion), alTocarSemestre);
    }

    @Override
    public int getItemCount() {
        return semestres.size();
    }

    static class SemestreVista extends RecyclerView.ViewHolder {

        private final TextView periodo;
        private final TextView estado;
        private final TextView detalle;
        private final TextView promedio;
        private final View bloqueAvance;
        private final CircularProgressIndicator anilloAvance;
        private final TextView porcentajeAvance;
        private final TextView semana;

        SemestreVista(@NonNull View vista) {
            super(vista);
            periodo = vista.findViewById(R.id.periodo);
            estado = vista.findViewById(R.id.estado);
            detalle = vista.findViewById(R.id.detalle);
            promedio = vista.findViewById(R.id.promedio);
            bloqueAvance = vista.findViewById(R.id.bloqueAvance);
            anilloAvance = vista.findViewById(R.id.anilloAvance);
            porcentajeAvance = vista.findViewById(R.id.porcentajeAvance);
            semana = vista.findViewById(R.id.semana);
        }

        void pintar(SemestreEnPantalla semestre, AlTocarSemestre alTocar) {
            periodo.setText(semestre.periodo);

            // "En curso" se resalta; "Terminado" va neutro, porque el acento_suave
            // está reservado para los chips de Meta y Necesitas (doc 04 · 4)
            estado.setText(semestre.terminado
                    ? R.string.semestres_terminado
                    : R.string.semestres_en_curso);
            estado.setBackgroundResource(semestre.terminado
                    ? R.drawable.fondo_chip_neutro
                    : R.drawable.fondo_chip_meta);
            estado.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    semestre.terminado ? R.color.texto_secundario : R.color.acento_oscuro));
            detalle.setText(itemView.getResources().getString(
                    R.string.semestres_materias_creditos, semestre.materias, semestre.creditos));
            promedio.setText(Formato.nota(semestre.promedio));
            pintarAvance(semestre);

            itemView.setOnClickListener(v -> alTocar.alTocar(semestre.id));
        }

        /**
         * Anillo con cuánto va del semestre, solo en el que está en curso. El lector de
         * pantalla lo lee como una frase completa (semana y porcentaje), no como "75 %".
         */
        private void pintarAvance(SemestreEnPantalla semestre) {
            Integer avance = semestre.porcentajeAvance();
            boolean hayAvance = avance != null;
            bloqueAvance.setVisibility(hayAvance ? View.VISIBLE : View.GONE);
            semana.setVisibility(hayAvance ? View.VISIBLE : View.GONE);
            if (!hayAvance) {
                itemView.setContentDescription(null);
                return;
            }
            anilloAvance.setProgress(avance);
            porcentajeAvance.setText(itemView.getResources().getString(
                    R.string.semestres_avance, avance));
            semana.setText(itemView.getResources().getString(
                    R.string.semestres_semana, semestre.semanaActual, semestre.semanasTotales));
            bloqueAvance.setContentDescription(itemView.getResources().getString(
                    R.string.semestres_desc_avance, semestre.semanaActual,
                    semestre.semanasTotales, avance));
        }
    }
}
