package com.dosnueve.app.ui.acceso;

import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;

/**
 * 01 Bienvenida (doc 03), estilo plano.
 *
 * <p>Solo presenta la app. Las dos salidas llevan a la misma pantalla 02 Entrar:
 * "Empezar" para quien llega nuevo y "Entrar" para quien ya tiene cuenta.
 */
public class BienvenidaFragment extends Fragment {

    private static final long DURACION_CAIDA = 450L;
    private static final long ESPERA_ANTES_DE_CAER = 120L;
    /** Entre la boleta y cada pieza de atrás */
    private static final long ESCALON = 90L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_bienvenida, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        vista.findViewById(R.id.botonEmpezar).setOnClickListener(v -> irAEntrar());
        vista.findViewById(R.id.botonEntrar).setOnClickListener(v -> irAEntrar());

        if (estado == null && hayAnimaciones()) {
            // Primero la boleta y, detrás, las dos piezas que se asoman
            dejarCaer(vista.findViewById(R.id.boleta), ESPERA_ANTES_DE_CAER);
            dejarCaer(vista.findViewById(R.id.piezaMateria), ESPERA_ANTES_DE_CAER + ESCALON);
            dejarCaer(vista.findViewById(R.id.piezaSemestre), ESPERA_ANTES_DE_CAER + 2 * ESCALON);
        }
    }

    /**
     * Si el sistema tiene las animaciones apagadas todo aparece ya en su lugar: no se le
     * impone movimiento a quien pidió no tenerlo.
     */
    private boolean hayAnimaciones() {
        return Settings.Global.getFloat(requireContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    /**
     * La pieza cae desde arriba y se asienta en su ángulo, como una hoja que se tira
     * sobre la mesa.
     */
    private void dejarCaer(View pieza, long espera) {
        float rotacionFinal = pieza.getRotation();
        float caida = getResources().getDisplayMetrics().density * 32;

        pieza.setAlpha(0f);
        pieza.setTranslationY(-caida);
        pieza.setRotation(rotacionFinal - 6f);
        pieza.animate()
                .alpha(1f)
                .translationY(0f)
                .rotation(rotacionFinal)
                .setStartDelay(espera)
                .setDuration(DURACION_CAIDA)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();
    }

    private void irAEntrar() {
        NavHostFragment.findNavController(this).navigate(R.id.entrarFragment);
    }
}
