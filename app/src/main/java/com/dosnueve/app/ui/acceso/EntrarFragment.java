package com.dosnueve.app.ui.acceso;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Cuenta;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 02 Entrar · 25 Error al entrar (doc 03, RF-03, RF-04 y RF-06).
 *
 * <p>El mismo campo acepta correo o nombre de usuario. Si no lleva arroba, hay que
 * leer {@code usernames/{usuario}} para sacar el correo antes de llamar a Firebase
 * (doc 06 · 4).
 *
 * <p>Cuando falla, el mensaje es uno solo —"Usuario o contraseña incorrectos"— sin
 * decir cuál de los dos: decirlo le confirmaría a un atacante qué cuentas existen.
 */
public class EntrarFragment extends Fragment {

    private TextInputLayout campoIdentificador;
    private TextInputLayout campoContrasena;
    private TextInputEditText identificador;
    private TextInputEditText contrasena;
    private MaterialButton botonEntrar;
    private View cargando;
    private View bloqueError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_entrar, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        campoIdentificador = vista.findViewById(R.id.campoIdentificador);
        campoContrasena = vista.findViewById(R.id.campoContrasena);
        identificador = vista.findViewById(R.id.identificador);
        contrasena = vista.findViewById(R.id.contrasena);
        botonEntrar = vista.findViewById(R.id.botonEntrar);
        cargando = vista.findViewById(R.id.cargando);
        bloqueError = vista.findViewById(R.id.bloqueError);

        ocultarError();
        mostrarCargando(false);

        limpiarErrorAlEscribir(identificador, campoIdentificador);
        limpiarErrorAlEscribir(contrasena, campoContrasena);

        botonEntrar.setOnClickListener(v -> entrar());
        // "Listo" en el teclado, después de la contraseña, entra sin tener que bajar al botón
        contrasena.setOnEditorActionListener((campo, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                entrar();
                return true;
            }
            return false;
        });
        vista.findViewById(R.id.botonGoogle).setOnClickListener(v -> entrarConGoogle());
        vista.findViewById(R.id.botonOlvide).setOnClickListener(v ->
                irA(R.id.olvideContrasenaFragment));
        vista.findViewById(R.id.botonCrearCuenta).setOnClickListener(v ->
                irA(R.id.crearCuentaFragment));
        vista.findViewById(R.id.botonVolver).setOnClickListener(v -> volver());
    }

    // ===== Entrar con correo o usuario =====

    private void entrar() {
        String quien = texto(identificador);
        String clave = texto(contrasena);

        boolean falta = quien.isEmpty();
        campoIdentificador.setError(falta ? getString(R.string.entrar_error_identificador) : null);

        boolean sinClave = clave.isEmpty();
        campoContrasena.setError(sinClave ? getString(R.string.entrar_error_contrasena) : null);

        if (falta || sinClave) {
            // El cursor va al primer campo que falta, y el lector de pantalla lee su error
            (falta ? identificador : contrasena).requestFocus();
            return;
        }

        ocultarError();
        mostrarCargando(true);
        Cuenta.entrar(quien, clave).addOnCompleteListener(entrada -> {
            if (!isAdded()) {
                return;
            }
            mostrarCargando(false);
            if (entrada.isSuccessful()) {
                Llegada.ir(this, entrada.getResult());
            } else {
                mostrarFalla(Cuenta.queFallo(entrada.getException()));
            }
        });
    }

    private void entrarConGoogle() {
        ocultarError();
        mostrarCargando(true);
        Cuenta.entrarConGoogle(requireActivity()).addOnCompleteListener(entrada -> {
            if (!isAdded()) {
                return;
            }
            mostrarCargando(false);
            if (entrada.isSuccessful()) {
                Llegada.ir(this, entrada.getResult());
            } else {
                mostrarFalla(Cuenta.queFallo(entrada.getException()));
            }
        });
    }

    private void mostrarFalla(Cuenta.Falla falla) {
        switch (falla) {
            case CANCELADO:
                // Cerró el selector de Google: no hay nada que decirle
                break;
            case CREDENCIALES:
                mostrarError();
                break;
            case SIN_INTERNET:
                mostrarError(R.string.error_login_sin_internet_titulo,
                        R.string.error_login_sin_internet, false);
                break;
            case MUCHOS_INTENTOS:
                mostrarError(R.string.error_muchos_intentos_titulo,
                        R.string.error_muchos_intentos, false);
                break;
            case SIN_CUENTA_GOOGLE:
                mostrarError(R.string.error_login_titulo, R.string.error_google_sin_cuenta, false);
                break;
            default:
                mostrarError(R.string.error_login_titulo, R.string.error_algo_fallo, false);
                break;
        }
    }

    // ===== Estados =====

    private void mostrarCargando(boolean entrando) {
        botonEntrar.setEnabled(!entrando);
        // Sin texto mientras carga: el indicador va centrado y se le montaría encima
        botonEntrar.setText(entrando ? "" : getString(R.string.entrar_boton));
        botonEntrar.setContentDescription(getString(R.string.entrar_boton));
        cargando.setVisibility(entrando ? View.VISIBLE : View.GONE);
    }

    /** 25 Error al entrar: mensaje único y el campo de contraseña en rojo (RF-06). */
    private void mostrarError() {
        mostrarError(R.string.entrar_error_credenciales,
                R.string.entrar_error_credenciales_detalle, true);
    }

    /** Si no es la contraseña (sin internet, Google…), el campo no se pone en rojo. */
    private void mostrarError(@StringRes int titulo, @StringRes int detalle, boolean esLaClave) {
        TextView comoSeLlama = bloqueError.findViewById(R.id.errorTitulo);
        TextView queDice = bloqueError.findViewById(R.id.errorDetalle);
        comoSeLlama.setText(titulo);
        queDice.setText(detalle);

        bloqueError.setVisibility(View.VISIBLE);
        if (esLaClave) {
            campoContrasena.setError(" ");
            contrasena.requestFocus();
        }
        bloqueError.announceForAccessibility(getString(titulo) + ". " + getString(detalle));
    }

    private void ocultarError() {
        bloqueError.setVisibility(View.GONE);
        campoIdentificador.setError(null);
        campoContrasena.setError(null);
    }

    /** El error se va en cuanto el usuario corrige algo. */
    private void limpiarErrorAlEscribir(TextInputEditText campo, TextInputLayout contenedor) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable t) {
                contenedor.setError(null);
                bloqueError.setVisibility(View.GONE);
            }
        });
    }

    private String texto(TextInputEditText campo) {
        CharSequence valor = campo.getText();
        return valor == null ? "" : valor.toString().trim();
    }

    private void irA(int destino) {
        NavHostFragment.findNavController(this).navigate(destino);
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
