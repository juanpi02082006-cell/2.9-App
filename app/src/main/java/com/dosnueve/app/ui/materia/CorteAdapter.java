package com.dosnueve.app.ui.materia;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.dosnueve.app.util.Formato;

import java.util.ArrayList;
import java.util.List;

/** Lista de cortes del detalle de materia (pantalla 08). Cada tarjeta lleva al 09. */
public class CorteAdapter extends RecyclerView.Adapter<CorteAdapter.CorteVista> {

    public interface AlTocarCorte {
        void alTocar(long corteId);
    }

    /** Lo que se pinta de un corte. Los números ya vienen calculados (RN-12). */
    public static class CorteEnPantalla {

        public final long id;
        public final String nombre;
        public final double porcentaje;
        /** Nota del corte, o {@code null} si todavía no está evaluado (RN-06). */
        public final Double nota;
        /** Meta del corte: solo en los cortes que faltan (doc 02 · 3.4). */
        public final Double meta;
        public final String[] componentes;
        public final Double[] notasComponentes;

        public CorteEnPantalla(long id, String nombre, double porcentaje, Double nota, Double meta,
                               String[] componentes, Double[] notasComponentes) {
            this.id = id;
            this.nombre = nombre;
            this.porcentaje = porcentaje;
            this.nota = nota;
            this.meta = meta;
            this.componentes = componentes;
            this.notasComponentes = notasComponentes;
        }
    }

    private final List<CorteEnPantalla> cortes = new ArrayList<>();
    private final AlTocarCorte alTocarCorte;

    public CorteAdapter(AlTocarCorte alTocarCorte) {
        this.alTocarCorte = alTocarCorte;
    }

    public void mostrar(List<CorteEnPantalla> nuevos) {
        cortes.clear();
        cortes.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CorteVista onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_corte, padre, false);
        return new CorteVista(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull CorteVista vista, int posicion) {
        vista.pintar(cortes.get(posicion), alTocarCorte);
    }

    @Override
    public int getItemCount() {
        return cortes.size();
    }

    static class CorteVista extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView vale;
        private final TextView chipMeta;
        private final TextView nota;
        private final LinearLayout listaComponentes;

        CorteVista(@NonNull View vista) {
            super(vista);
            nombre = vista.findViewById(R.id.nombreCorte);
            vale = vista.findViewById(R.id.valeCorte);
            chipMeta = vista.findViewById(R.id.chipMeta);
            nota = vista.findViewById(R.id.notaCorte);
            listaComponentes = vista.findViewById(R.id.listaComponentes);
        }

        void pintar(CorteEnPantalla corte, AlTocarCorte alTocar) {
            nombre.setText(corte.nombre);
            vale.setText(itemView.getResources()
                    .getString(R.string.corte_vale, Formato.porcentaje(corte.porcentaje)));

            nota.setText(Formato.nota(corte.nota));

            boolean tieneMeta = corte.meta != null;
            chipMeta.setVisibility(tieneMeta ? View.VISIBLE : View.GONE);
            if (tieneMeta) {
                chipMeta.setText(itemView.getResources()
                        .getString(R.string.corte_meta, Formato.nota(corte.meta)));
            }

            listaComponentes.removeAllViews();
            for (int i = 0; i < corte.componentes.length; i++) {
                View renglon = LayoutInflater.from(itemView.getContext())
                        .inflate(R.layout.item_componente_resumen, listaComponentes, false);

                TextView comoSeLlama = renglon.findViewById(R.id.nombre);
                TextView suNota = renglon.findViewById(R.id.nota);
                comoSeLlama.setText(corte.componentes[i]);
                suNota.setText(Formato.nota(
                        i < corte.notasComponentes.length ? corte.notasComponentes[i] : null));

                listaComponentes.addView(renglon);
            }

            itemView.setOnClickListener(v -> alTocar.alTocar(corte.id));
        }
    }
}
