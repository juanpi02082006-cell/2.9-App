package com.dosnueve.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.dosnueve.app.avisos.Avisos;
import com.dosnueve.app.data.Conexion;
import com.dosnueve.app.data.Cuenta;
import com.dosnueve.app.data.Repositorio;

/**
 * Arranque de la app.
 *
 * <p>2.9 se ve siempre color papel, aunque el celular esté en modo oscuro: el doc 04
 * define un único concepto visual (el cuaderno y el lapicero rojo del profe) y no
 * trae variante oscura. Fijarlo aquí, y no en la Activity, evita que la pantalla
 * parpadee al recrearse.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        Repositorio.iniciar(this);
        Cuenta.iniciar();
        Conexion.iniciar(this);
        Avisos.crearCanales(this);
    }
}
