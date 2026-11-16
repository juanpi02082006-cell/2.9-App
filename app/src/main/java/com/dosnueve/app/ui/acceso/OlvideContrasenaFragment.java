package com.dosnueve.app.ui.acceso;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.util.Validaciones;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 05 Olvidaste tu contraseña (doc 03, RF-05).
 *
 * <p>Solo manda el enlace; la página para escribir la nueva contraseña la pone
 * Firebase. Se vuelve a 02 Entrar con el aviso de que ya salió.
 */
public class OlvideContrasenaFragment extends Fragment {

    private TextInputLayout campoCorreo;
    private TextInputEditText correo;
    private MaterialButton botonEnviar;
    private View cargando;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_olvide_contrasena, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        campoCorreo = vista.findViewById(R.id.campoCorreo);
        correo = vista.findViewById(R.id.correo);
        botonEnviar = vista.findViewById(R.id.botonEnviar);
        cargando = vista.findViewById(R.id.cargando);

        mostrarCargando(false);
        botonEnviar.setOnClickListener(v -> enviarEnlace());
        vista.findViewById(R.id.botonVolver).setOnClickListener(v -> volver());
    }

    private void enviarEnlace() {
        String escrito = correo.getText() == null ? "" : correo.getText().toString().trim();

        if (!Validaciones.correoValido(escrito)) {
            campoCorreo.setError(getString(R.string.crear_error_correo));
            return;
        }
        campoCorreo.setError(null);
        mostrarCargando(true);

        // Fase 2: sendPasswordResetEmail(escrito).
        // Se avisa igual exista o no la cuenta: decir "ese correo no está registrado"
        // revelaría quién tiene cuenta.
        botonEnviar.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            mostrarCargando(false);
            Snackbar.make(requireView(), R.string.olvide_enviado, Snackbar.LENGTH_LONG).show();
            volver();
        }, 500L);
    }

    private void mostrarCargando(boolean enviando) {
        botonEnviar.setEnabled(!enviando);
        botonEnviar.setText(enviando ? "" : getString(R.string.olvide_boton));
        botonEnviar.setContentDescription(getString(R.string.olvide_boton));
        cargando.setVisibility(enviando ? View.VISIBLE : View.GONE);
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
