package com.dosnueve.app.ui.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dosnueve.app.R;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.ArrayList;
import java.util.List;

/** Lista de universidades con selección única (pantalla 02). */
public class UniversidadAdapter extends RecyclerView.Adapter<UniversidadAdapter.FilaVista> {

    public interface AlEscoger {
        void alEscoger(Universidad universidad);
    }

    private final List<Universidad> universidades = new ArrayList<>();
    private final AlEscoger alEscoger;
    private int escogida = RecyclerView.NO_POSITION;

    public UniversidadAdapter(AlEscoger alEscoger) {
        this.alEscoger = alEscoger;
    }

    public void mostrar(List<Universidad> nuevas) {
        universidades.clear();
        universidades.addAll(nuevas);
        escogida = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FilaVista onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_universidad, padre, false);
        return new FilaVista(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull FilaVista vista, int posicion) {
        Universidad universidad = universidades.get(posicion);
        vista.pintar(universidad, posicion == escogida, posicion == universidades.size() - 1);
        vista.itemView.setOnClickListener(v -> {
            int anterior = escogida;
            escogida = vista.getBindingAdapterPosition();
            if (anterior != RecyclerView.NO_POSITION) {
                notifyItemChanged(anterior);
            }
            notifyItemChanged(escogida);
            alEscoger.alEscoger(universidad);
        });
    }

    @Override
    public int getItemCount() {
        return universidades.size();
    }

    static class FilaVista extends RecyclerView.ViewHolder {

        private final MaterialRadioButton radio;
        private final TextView nombre;
        private final TextView formaCalificar;
        private final View divisor;

        FilaVista(@NonNull View vista) {
            super(vista);
            radio = vista.findViewById(R.id.radio);
            nombre = vista.findViewById(R.id.nombre);
            formaCalificar = vista.findViewById(R.id.formaCalificar);
            divisor = vista.findViewById(R.id.divisor);
        }

        void pintar(Universidad universidad, boolean escogida, boolean esLaUltima) {
            nombre.setText(universidad.nombre);
            formaCalificar.setText(universidad.formaCalificar);
            radio.setChecked(escogida);
            divisor.setVisibility(esLaUltima ? View.GONE : View.VISIBLE);
        }
    }
}
