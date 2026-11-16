package com.dosnueve.app.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * ¿Hay Internet? Para el aviso "Sin conexión" del Inicio (pantalla 20, RF-31).
 *
 * <p>Sin Internet la app sigue funcionando (Firestore guarda en el celular y sube después);
 * el aviso solo cuenta que lo nuevo todavía no está en la nube.
 */
public final class Conexion {

    private static final MutableLiveData<Boolean> hayInternet = new MutableLiveData<>(true);

    private Conexion() {
    }

    /** Se llama una vez, al arrancar la app. */
    public static void iniciar(Context contexto) {
        ConnectivityManager red = contexto.getSystemService(ConnectivityManager.class);
        if (red == null) {
            return;
        }
        hayInternet.setValue(sirve(red.getNetworkCapabilities(red.getActiveNetwork())));
        red.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(@NonNull Network cual,
                                              @NonNull NetworkCapabilities capacidades) {
                hayInternet.postValue(sirve(capacidades));
            }

            @Override
            public void onLost(@NonNull Network cual) {
                hayInternet.postValue(false);
            }
        });
    }

    public static LiveData<Boolean> hayInternet() {
        return hayInternet;
    }

    /** Conectada y comprobada (un wifi que pide iniciar sesión no cuenta). */
    private static boolean sirve(@Nullable NetworkCapabilities capacidades) {
        return capacidades != null
                && capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
