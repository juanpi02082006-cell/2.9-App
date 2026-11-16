package com.dosnueve.app.ui.acceso;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Cuenta;
import com.google.android.material.button.MaterialButton;

/**
 * 04 Verifica tu correo (doc 03, RF-02).
 *
 * <p>"Ya lo confirmé" hace reload() del usuario y revisa isEmailVerified(). No hay
 * forma de enterarse sola: Firebase no avisa cuando el usuario abre el enlace, por
 * eso el botón lo pregunta de nuevo.
 */
public class VerificaCorreoFragment extends Fragment {

    private static final String ARG_CORREO = "correo";

    private MaterialButton botonConfirme;
    private View cargando;
    private TextView aviso;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_verifica_correo, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        botonConfirme = vista.findViewById(R.id.botonConfirme);
        cargando = vista.findViewById(R.id.cargando);
        aviso = vista.findViewById(R.id.aviso);

        aviso.setVisibility(View.GONE);
        mostrarCargando(false);

        String correo = getArguments() == null ? "" : getArguments().getString(ARG_CORREO, "");
        TextView detalle = vista.findViewById(R.id.detalle);
        detalle.setText(getString(R.string.verifica_detalle, correo));

        botonConfirme.setOnClickListener(v -> revisarSiYaConfirmo());
        vista.findViewById(R.id.botonReenviar).setOnClickListener(v -> reenviar());
    }

    private void revisarSiYaConfirmo() {
        aviso.setVisibility(View.GONE);
        mostrarCargando(true);
        Cuenta.yaConfirmoElCorreo().addOnCompleteListener(revision -> {
            if (!isAdded()) {
                return;
            }
            mostrarCargando(false);
            if (!revision.isSuccessful()) {
                mostrarAviso(mensajeDeFalla(revision.getException()));
            } else if (Boolean.TRUE.equals(revision.getResult())) {
                NavHostFragment.findNavController(this).navigate(R.id.universidadFragment);
            } else {
                mostrarAviso(R.string.verifica_sin_confirmar);
            }
        });
    }

    private void reenviar() {
        Cuenta.reenviarVerificacion().addOnCompleteListener(envio -> {
            if (isAdded()) {
                mostrarAviso(envio.isSuccessful()
                        ? R.string.verifica_reenviado
                        : mensajeDeFalla(envio.getException()));
            }
        });
    }

    @StringRes
    private int mensajeDeFalla(@Nullable Exception error) {
        switch (Cuenta.queFallo(error)) {
            case SIN_INTERNET:
                return R.string.error_sin_internet_corto;
            case MUCHOS_INTENTOS:
                return R.string.error_muchos_intentos;
            default:
                return R.string.error_algo_fallo;
        }
    }

    private void mostrarAviso(@StringRes int mensaje) {
        aviso.setText(mensaje);
        aviso.setVisibility(View.VISIBLE);
        aviso.announceForAccessibility(getString(mensaje));
    }

    private void mostrarCargando(boolean revisando) {
        botonConfirme.setEnabled(!revisando);
        botonConfirme.setText(revisando ? "" : getString(R.string.verifica_confirme));
        botonConfirme.setContentDescription(getString(R.string.verifica_confirme));
        cargando.setVisibility(revisando ? View.VISIBLE : View.GONE);
    }
}
