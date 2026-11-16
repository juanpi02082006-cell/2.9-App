package com.dosnueve.app.ui.acceso;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.util.Validaciones;
import com.google.android.material.button.MaterialButton;
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

        // Fase 2 (doc 06 · 4):
        //  1. revisar que usernames/{usuario} no exista → si existe, usuarioOcupado()
        //  2. createUserWithEmailAndPassword
        //  3. WriteBatch: usernames/{usuario} + usuarios/{uid} con proveedor "correo"
        //  4. sendEmailVerification() → pantalla 04
        botonCrear.postDelayed(() -> {
            if (isAdded()) {
                irAVerificar();
            }
        }, 500L);
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

    /** Lo llama el repositorio cuando el usuario ya está tomado. */
    public void usuarioOcupado() {
        mostrarCargando(false);
        campoUsuario.setError(getString(R.string.crear_error_usuario_ocupado));
        campoUsuario.requestFocus();
    }

    private void registrarseConGoogle() {
        mostrarCargando(true);

        // Fase 2: igual que en 02, pero si la cuenta es nueva se le sugiere un
        // usuario a partir del correo (Validaciones.usuarioSugerido)
        botonCrear.postDelayed(() -> {
            if (isAdded()) {
                NavHostFragment.findNavController(this).navigate(R.id.universidadFragment);
            }
        }, 450L);
    }

    private void irAVerificar() {
        mostrarCargando(false);
        // Fase 2: esto lo guardará Firebase; por ahora queda en el celular
        Repositorio.get().guardarCuenta(texto(nombre), texto(correo));
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
