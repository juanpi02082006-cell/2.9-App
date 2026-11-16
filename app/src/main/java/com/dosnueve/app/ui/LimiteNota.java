package com.dosnueve.app.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import com.dosnueve.app.R;
import com.dosnueve.app.domain.CalculadoraNotas;

/**
 * Las notas van de 0.0 a 5.0 (RN-01). Si en un campo de nota se escribe más, se avisa
 * "Nota máxima 5.0, vuelve a poner la nota" y el campo se vacía para escribirla otra vez.
 *
 * <p>El aviso es un Toast y no un Snackbar porque algunos campos están dentro de una hoja
 * inferior, que es otra ventana: un Snackbar quedaría detrás de ella.
 */
public final class LimiteNota {

    private LimiteNota() {
    }

    public static void vigilar(EditText campo) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                if (pasaDelMaximo(texto.toString())) {
                    texto.clear();
                    Toast.makeText(campo.getContext(), R.string.nota_maxima, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private static boolean pasaDelMaximo(String texto) {
        try {
            return Double.parseDouble(texto.trim().replace(',', '.')) > CalculadoraNotas.NOTA_MAXIMA;
        } catch (NumberFormatException sinNumero) {
            return false;
        }
    }
}
