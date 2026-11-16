package com.dosnueve.app.ui.acceso;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Cuenta;

/** A dónde se va después de entrar o de crear cuenta (pantallas 02 y 03). */
final class Llegada {

    private Llegada() {
    }

    static void ir(Fragment desde, Cuenta.Destino destino) {
        switch (destino) {
            case INICIO:
                // Sin poder volver atrás al login
                NavHostFragment.findNavController(desde).navigate(R.id.inicioFragment, null,
                        new NavOptions.Builder().setPopUpTo(R.id.bienvenidaFragment, true).build());
                break;
            case UNIVERSIDAD:
                NavHostFragment.findNavController(desde).navigate(R.id.universidadFragment);
                break;
            case VERIFICAR:
                Bundle argumentos = new Bundle();
                String correo = Cuenta.correoActual();
                argumentos.putString("correo", correo == null ? "" : correo);
                NavHostFragment.findNavController(desde)
                        .navigate(R.id.verificaCorreoFragment, argumentos);
                break;
        }
    }
}
