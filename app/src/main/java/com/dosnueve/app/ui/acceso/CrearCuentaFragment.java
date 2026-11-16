package com.dosnueve.app.ui.acceso;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Cuenta;
import com.dosnueve.app.util.Validaciones;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 03 Crear cuenta (doc 03, RF-01 y RF-04).
 *
 * <p>Cuatro datos. El nombre de usuario tiene que ser único, y eso solo se sabe
 * consultando la colección {@code usernames}: por eso "Ese usuario ya está ocupado"
 * puede aparecer después de tocar el botón, no mientras se escribe.
 */
public class CrearCuentaFragment extends Fragment {

    private TextInputLayout campoNombre;
    private TextInputLayout campoUsuario;
    private TextInputLayout campoCorreo;
    private TextInputLayout campoContrasena;
    private TextInputEditText nombre;
    private TextInputEditText usuario;
    private TextInputEditText correo;
    private TextInputEditText contrasena;
    private MaterialButton botonCrear;
    private View cargando;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_crear_cuenta, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        campoNombre = vista.findViewById(R.id.campoNombre);
        campoUsuario = vista.findViewById(R.id.campoUsuario);
        campoCorreo = vista.findViewById(R.id.campoCorreo);
        campoContrasena = vista.findViewById(R.id.campoContrasena);
        nombre = vista.findViewById(R.id.nombre);
        usuario = vista.findViewById(R.id.usuario);
        correo = vista.findViewById(R.id.correo);
        contrasena = vista.findViewById(R.id.contrasena);
        botonCrear = vista.findViewById(R.id.botonCrear);
        cargando = vista.findViewById(R.id.cargando);

        mostrarCargando(false);

        limpiarErrorAlEscribir(nombre, campoNombre);
        limpiarErrorAlEscribir(usuario, campoUsuario);
        limpiarErrorAlEscribir(correo, campoCorreo);
        limpiarErrorAlEscribir(contrasena, campoContrasena);

        // El usuario se guarda en minúsculas; escribirlo con mayúsculas no debería
        // crear una cuenta distinta
        usuario.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                String escrito = texto.toString();
                String minusculas = escrito.toLowerCase();
                if (!escrito.equals(minusculas)) {
                    int donde = usuario.getSelectionStart();
                    usuario.setText(minusculas);
                    usuario.setSelection(Math.min(donde, minusculas.length()));
                }
            }
        });

        botonCrear.setOnClickListener(v -> crearCuenta());
        vista.findViewById(R.id.botonGoogle).setOnClickListener(v -> registrarseConGoogle());
        vista.findViewById(R.id.botonEntrar).setOnClickListener(v -> volver());
        vista.findViewById(R.id.botonVolver).setOnClickListener(v -> volver());
    }

    private void crearCuenta() {
        if (!todoEstaBien()) {
            return;
        }
        mostrarCargando(true);
        Cuenta.crearCuenta(texto(nombre), texto(usuario), texto(correo), texto(contrasena))
                .addOnCompleteListener(creada -> {
                    if (!isAdded()) {
                        return;
                    }
                    mostrarCargando(false);
                    if (creada.isSuccessful()) {
                        irAVerificar();
                    } else {
                        mostrarFalla(Cuenta.queFallo(creada.getException()));
                    }
                });
    }

    private void mostrarFalla(Cuenta.Falla falla) {
        switch (falla) {
            case CANCELADO:
                break;
            case USUARIO_OCUPADO:
                usuarioOcupado();
                break;
            case CORREO_OCUPADO:
                campoCorreo.setError(getString(R.string.crear_error_correo_ocupado));
                correo.requestFocus();
                break;
            case CREDENCIALES:
                campoCorreo.setError(getString(R.string.crear_error_correo));
                correo.requestFocus();
                break;
            case SIN_INTERNET:
                avisar(R.string.error_sin_internet_corto);
                break;
            case MUCHOS_INTENTOS:
                avisar(R.string.error_muchos_intentos);
                break;
            case SIN_CUENTA_GOOGLE:
                avisar(R.string.error_google_sin_cuenta);
                break;
            default:
                avisar(R.string.error_algo_fallo);
                break;
        }
    }

    private void avisar(@StringRes int mensaje) {
        Snackbar.make(requireView(), mensaje, Snackbar.LENGTH_LONG).show();
    }

    /** Valida los cuatro campos y marca todos los que fallen, no solo el primero. */
    private boolean todoEstaBien() {
        boolean bien = true;

        if (!Validaciones.nombreValido(texto(nombre))) {
            campoNombre.setError(getString(R.string.crear_error_nombre));
            bien = false;
        }
        if (!Validaciones.usuarioValido(texto(usuario))) {
            campoUsuario.setError(getString(R.string.crear_error_usuario));
            bien = false;
        }
        if (!Validaciones.correoValido(texto(correo))) {
            campoCorreo.setError(getString(R.string.crear_error_correo));
            bien = false;
        }
        if (!Validaciones.contrasenaValida(texto(contrasena))) {
            campoContrasena.setError(getString(R.string.crear_error_contrasena));
            bien = false;
        }
        return bien;
    }

    private void usuarioOcupado() {
        campoUsuario.setError(getString(R.string.crear_error_usuario_ocupado));
        usuario.requestFocus();
    }

    /** Igual que en 02: si la cuenta es nueva, el usuario sale del correo (juanma, juanma2…). */
    private void registrarseConGoogle() {
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

    private void irAVerificar() {
        Bundle argumentos = new Bundle();
        argumentos.putString("correo", texto(correo));
        NavHostFragment.findNavController(this)
                .navigate(R.id.verificaCorreoFragment, argumentos);
    }

    private void mostrarCargando(boolean creando) {
        botonCrear.setEnabled(!creando);
        botonCrear.setText(creando ? "" : getString(R.string.crear_boton));
        botonCrear.setContentDescription(getString(R.string.crear_boton));
        cargando.setVisibility(creando ? View.VISIBLE : View.GONE);
    }

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
            }
        });
    }

    private String texto(TextInputEditText campo) {
        CharSequence valor = campo.getText();
        return valor == null ? "" : valor.toString().trim();
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
