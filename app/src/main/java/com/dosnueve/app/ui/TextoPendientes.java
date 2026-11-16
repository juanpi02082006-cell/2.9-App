package com.dosnueve.app.ui;

import android.content.Context;
import android.text.TextUtils;

import com.dosnueve.app.R;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.util.Formato;

import java.util.ArrayList;
import java.util.List;

/**
 * Dice en qué se necesita la nota de "cuánto necesitas" en un corte: "el parcial",
 * "la autoevaluación" o "el parcial y el 10 % que falta de trabajos". Lo usan el detalle
 * de corte, la hoja de "+ Nota" y Registrar nota, para que digan lo mismo.
 */
public final class TextoPendientes {

    private TextoPendientes() {
    }

    public static String describir(Context contexto, List<CalculadoraNotas.Pendiente> pendientes) {
        List<String> partes = new ArrayList<>();
        for (CalculadoraNotas.Pendiente pendiente : pendientes) {
            partes.add(nombre(contexto, pendiente));
        }
        if (partes.isEmpty()) {
            return contexto.getString(R.string.hoja_lo_que_falta);
        }
        if (partes.size() == 1) {
            return partes.get(0);
        }
        String ultima = partes.remove(partes.size() - 1);
        return TextUtils.join(", ", partes) + contexto.getString(R.string.texto_y) + ultima;
    }

    private static String nombre(Context contexto, CalculadoraNotas.Pendiente pendiente) {
        switch (pendiente.componenteId) {
            case "parcial":
                return contexto.getString(R.string.hoja_el_parcial);
            case "auto":
                return contexto.getString(R.string.hoja_la_autoevaluacion);
            case "trabajos":
                return pendiente.parcial
                        ? contexto.getString(R.string.hoja_parte_de_trabajos,
                                Formato.porcentaje(pendiente.porcentaje))
                        : contexto.getString(R.string.hoja_trabajos);
            default:
                return contexto.getString(R.string.hoja_lo_que_falta);
        }
    }
}
