package com.dosnueve.app.data;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.dosnueve.app.R;
import com.dosnueve.app.util.Validaciones;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * La cuenta de la persona: entrar, crear cuenta, Google, verificar el correo y cerrar
 * sesión (doc 06 · 4). Habla con Firebase Auth y con las colecciones
 * {@code usernames} y {@code usuarios} de Firestore (doc 05).
 *
 * <p>Todo devuelve un {@link Task}; las pantallas solo miran si salió bien y, si no,
 * le preguntan a {@link #queFallo(Exception)} qué mensaje mostrar.
 */
public final class Cuenta {

    /** A dónde va la persona después de entrar. */
    public enum Destino { INICIO, UNIVERSIDAD, VERIFICAR }

    /** Lo que puede salir mal, ya traducido a lo que la pantalla necesita saber. */
    public enum Falla {
        CREDENCIALES, USUARIO_OCUPADO, CORREO_OCUPADO, SIN_INTERNET, MUCHOS_INTENTOS,
        CANCELADO, SIN_CUENTA_GOOGLE, OTRA
    }

    private static final String USERNAMES = "usernames";
    private static final String USUARIOS = "usuarios";
    private static final String PROVEEDOR_CORREO = "correo";
    private static final String PROVEEDOR_GOOGLE = "google";
    /** Cuántos números se prueban (juanma, juanma2, juanma3…) antes de rendirse. */
    private static final int INTENTOS_DE_USUARIO = 30;

    /** El usuario escrito no existe. Se muestra igual que una contraseña mala (RF-06). */
    private static final class UsuarioNoExiste extends Exception {
    }

    private static final class UsuarioOcupado extends Exception {
    }

    private Cuenta() {
    }

    /** Se llama una vez, al arrancar: los correos de Firebase salen en español. */
    public static void iniciar() {
        auth().setLanguageCode("es");
    }

    /** Hay sesión si hay alguien adentro con el correo confirmado (Google ya viene confirmado). */
    public static boolean haySesion() {
        FirebaseUser yo = auth().getCurrentUser();
        return yo != null && yo.isEmailVerified();
    }

    // ===== Entrar con correo o usuario (pantalla 02) =====

    public static Task<Destino> entrar(String quien, String clave) {
        String limpio = quien.trim();
        Task<String> correo;
        if (Validaciones.pareceCorreo(limpio)) {
            correo = Tasks.forResult(limpio);
        } else {
            // Con usuario: primero se busca su correo (doc 05 · 5)
            correo = db().collection(USERNAMES).document(limpio.toLowerCase())
                    .get(Source.SERVER)
                    .onSuccessTask(doc -> {
                        String suCorreo = doc.exists() ? doc.getString("correo") : null;
                        return suCorreo == null
                                ? Tasks.<String>forException(new UsuarioNoExiste())
                                : Tasks.forResult(suCorreo);
                    });
        }
        return correo
                .onSuccessTask(suCorreo -> auth().signInWithEmailAndPassword(suCorreo, clave))
                .onSuccessTask(resultado -> {
                    FirebaseUser yo = resultado.getUser();
                    if (yo == null) {
                        return Tasks.<Destino>forException(new IllegalStateException("Sin usuario"));
                    }
                    if (!yo.isEmailVerified()) {
                        return Tasks.forResult(Destino.VERIFICAR);
                    }
                    return cargarPerfil(yo).onSuccessTask(nuevo -> Tasks.forResult(Destino.INICIO));
                });
    }

    // ===== Crear cuenta (pantalla 03) =====

    /**
     * Revisa que el usuario esté libre, crea la cuenta, aparta el usuario y guarda el
     * perfil en un solo lote, y manda el correo de verificación.
     */
    public static Task<Void> crearCuenta(String nombre, String usuario, String correo, String clave) {
        String suUsuario = usuario.trim().toLowerCase();
        String suCorreo = correo.trim();
        String suNombre = nombre.trim();

        return db().collection(USERNAMES).document(suUsuario).get(Source.SERVER)
                .onSuccessTask(doc -> doc.exists()
                        ? Tasks.<AuthResult>forException(new UsuarioOcupado())
                        : auth().createUserWithEmailAndPassword(suCorreo, clave))
                .onSuccessTask(resultado -> {
                    FirebaseUser yo = resultado.getUser();
                    if (yo == null) {
                        return Tasks.<Void>forException(new IllegalStateException("Sin usuario"));
                    }
                    return guardarPerfilNuevo(yo, suNombre, suUsuario, suCorreo, PROVEEDOR_CORREO, null)
                            .continueWithTask(guardado -> {
                                if (guardado.isSuccessful()) {
                                    // Si el correo no sale, en la pantalla 04 está "Reenviar"
                                    return yo.sendEmailVerification().continueWith(envio -> (Void) null);
                                }
                                // Alguien apartó el usuario justo antes: se borra la cuenta
                                // recién hecha para que se pueda intentar con otro
                                Exception porQue = esPermisoNegado(guardado.getException())
                                        ? new UsuarioOcupado() : guardado.getException();
                                return yo.delete().continueWithTask(borrado ->
                                        Tasks.<Void>forException(porQue));
                            });
                })
                .onSuccessTask(listo -> {
                    Repositorio.get().guardarCuenta(suNombre, suCorreo);
                    return Tasks.<Void>forResult(null);
                });
    }

    // ===== Google (pantallas 02 y 03) =====

    /**
     * Abre el selector de cuentas de Google y entra a Firebase con la escogida. La primera
     * vez se le crea el perfil con un usuario sacado del correo y va a ¿Dónde estudias?
     */
    public static Task<Destino> entrarConGoogle(Activity actividad) {
        return cuentaDeGoogle(actividad)
                .onSuccessTask(google -> auth().signInWithCredential(
                        GoogleAuthProvider.getCredential(google.getIdToken(), null)))
                .onSuccessTask(resultado -> {
                    FirebaseUser yo = resultado.getUser();
                    if (yo == null) {
                        return Tasks.<Destino>forException(new IllegalStateException("Sin usuario"));
                    }
                    return cargarPerfil(yo).onSuccessTask(nuevo -> nuevo
                            ? crearPerfilDeGoogle(yo)
                            : Tasks.forResult(Destino.INICIO));
                });
    }

    /** Abre el selector de cuentas de Google del celular y devuelve la escogida. */
    private static Task<GoogleIdTokenCredential> cuentaDeGoogle(Activity actividad) {
        GetSignInWithGoogleOption opcion = new GetSignInWithGoogleOption.Builder(
                actividad.getString(R.string.default_web_client_id)).build();
        GetCredentialRequest pedido = new GetCredentialRequest.Builder()
                .addCredentialOption(opcion)
                .build();

        TaskCompletionSource<GoogleIdTokenCredential> cuenta = new TaskCompletionSource<>();
        CredentialManager.create(actividad).getCredentialAsync(actividad, pedido,
                new CancellationSignal(), ContextCompat.getMainExecutor(actividad),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse respuesta) {
                        Credential credencial = respuesta.getCredential();
                        if (credencial instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                .equals(credencial.getType())) {
                            try {
                                cuenta.setResult(GoogleIdTokenCredential.createFrom(credencial.getData()));
                            } catch (Exception error) {
                                cuenta.setException(error);
                            }
                        } else {
                            cuenta.setException(new IllegalStateException("Credencial inesperada"));
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException error) {
                        cuenta.setException(error);
                    }
                });
        return cuenta.getTask();
    }

    /**
     * Perfil → "Foto de Google" en una cuenta de correo: escoge su cuenta de Google, se
     * conecta a la de 2.9 (así también puede entrar con el botón de Google) y devuelve su
     * foto, o {@code null} si esa cuenta no tiene. Si conectar falla (por ejemplo, porque
     * esa cuenta de Google ya tiene su propia cuenta en 2.9), la foto igual sirve.
     */
    public static Task<String> conectarGoogle(Activity actividad) {
        return cuentaDeGoogle(actividad).onSuccessTask(google -> {
            Uri foto = google.getProfilePictureUri();
            FirebaseUser yo = auth().getCurrentUser();
            Task<AuthResult> conexion = yo == null
                    ? Tasks.<AuthResult>forResult(null)
                    : yo.linkWithCredential(GoogleAuthProvider.getCredential(google.getIdToken(), null));
            return conexion.continueWith(listo -> foto == null ? null : foto.toString());
        });
    }

    private static Task<Destino> crearPerfilDeGoogle(FirebaseUser yo) {
        String correo = yo.getEmail() == null ? "" : yo.getEmail();
        String nombre = yo.getDisplayName() == null ? "" : yo.getDisplayName();
        Uri foto = yo.getPhotoUrl();
        return usuarioLibre(Validaciones.usuarioSugerido(correo), 1)
                .onSuccessTask(usuario -> guardarPerfilNuevo(yo, nombre, usuario, correo,
                        PROVEEDOR_GOOGLE, foto == null ? null : foto.toString()))
                .onSuccessTask(listo -> {
                    Repositorio.get().guardarCuenta(nombre, correo);
                    return Tasks.forResult(Destino.UNIVERSIDAD);
                });
    }

    /** Prueba juanma, juanma2, juanma3… hasta dar con uno que nadie tenga. */
    private static Task<String> usuarioLibre(String base, int intento) {
        String candidato = Validaciones.usuarioConNumero(base, intento);
        return db().collection(USERNAMES).document(candidato).get(Source.SERVER)
                .onSuccessTask(doc -> {
                    if (!doc.exists()) {
                        return Tasks.forResult(candidato);
                    }
                    if (intento >= INTENTOS_DE_USUARIO) {
                        return Tasks.<String>forException(new UsuarioOcupado());
                    }
                    return usuarioLibre(base, intento + 1);
                });
    }

    // ===== Verificar el correo (pantalla 04) =====

    /** Firebase no avisa cuando se abre el enlace: hay que volver a preguntarle. */
    public static Task<Boolean> yaConfirmoElCorreo() {
        FirebaseUser yo = auth().getCurrentUser();
        if (yo == null) {
            return Tasks.forResult(false);
        }
        return yo.reload().onSuccessTask(listo -> {
            FirebaseUser actualizado = auth().getCurrentUser();
            if (actualizado == null || !actualizado.isEmailVerified()) {
                return Tasks.forResult(false);
            }
            return cargarPerfil(actualizado).onSuccessTask(nuevo -> Tasks.forResult(true));
        });
    }

    public static Task<Void> reenviarVerificacion() {
        FirebaseUser yo = auth().getCurrentUser();
        return yo == null ? Tasks.forResult(null) : yo.sendEmailVerification();
    }

    @Nullable
    public static String correoActual() {
        FirebaseUser yo = auth().getCurrentUser();
        return yo == null ? null : yo.getEmail();
    }

    // ===== Olvidé la contraseña (pantalla 05) =====

    /**
     * Si el correo no tiene cuenta, se responde como si hubiera salido: decir que no
     * existe revelaría quién está registrado.
     */
    public static Task<Void> recuperarContrasena(String correo) {
        return auth().sendPasswordResetEmail(correo.trim()).continueWithTask(envio -> {
            if (envio.isSuccessful() || envio.getException() instanceof FirebaseAuthInvalidUserException) {
                return Tasks.<Void>forResult(null);
            }
            return envio;
        });
    }

    // ===== Cerrar sesión =====

    /**
     * Sale de Firebase, borra de la memoria los datos de la cuenta y olvida la cuenta de
     * Google escogida, para poder cambiar de cuenta.
     */
    public static void cerrarSesion(Context contexto) {
        Repositorio.get().cerrar();
        auth().signOut();
        CredentialManager.create(contexto).clearCredentialStateAsync(
                new ClearCredentialStateRequest(), new CancellationSignal(),
                ContextCompat.getMainExecutor(contexto),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void nada) {
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException error) {
                    }
                });
    }

    // ===== Errores =====

    public static Falla queFallo(@Nullable Exception error) {
        if (error instanceof UsuarioOcupado) {
            return Falla.USUARIO_OCUPADO;
        }
        if (error instanceof UsuarioNoExiste
                || error instanceof FirebaseAuthInvalidCredentialsException
                || error instanceof FirebaseAuthInvalidUserException) {
            return Falla.CREDENCIALES;
        }
        if (error instanceof FirebaseAuthUserCollisionException) {
            return Falla.CORREO_OCUPADO;
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return Falla.MUCHOS_INTENTOS;
        }
        if (error instanceof FirebaseNetworkException
                || (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.UNAVAILABLE)) {
            return Falla.SIN_INTERNET;
        }
        if (error instanceof GetCredentialCancellationException) {
            return Falla.CANCELADO;
        }
        if (error instanceof NoCredentialException) {
            return Falla.SIN_CUENTA_GOOGLE;
        }
        return Falla.OTRA;
    }

    // ===== Perfil en Firestore =====

    /**
     * Lee {@code usuarios/{uid}} y pasa el nombre y el correo al repositorio. Devuelve
     * {@code true} si el perfil todavía no existe (cuenta nueva de Google).
     */
    private static Task<Boolean> cargarPerfil(FirebaseUser yo) {
        // Desde aquí el repositorio escucha las materias y notas de esta cuenta
        Repositorio.get().abrir(yo.getUid());
        return db().collection(USUARIOS).document(yo.getUid()).get()
                .onSuccessTask(doc -> {
                    if (!doc.exists()) {
                        return Tasks.forResult(true);
                    }
                    Repositorio.get().guardarCuenta(texto(doc, "nombre"), texto(doc, "correo"));
                    return Tasks.forResult(false);
                });
    }

    /** Aparta el usuario y crea el perfil a la vez: o quedan los dos, o ninguno. */
    private static Task<Void> guardarPerfilNuevo(FirebaseUser yo, String nombre, String usuario,
                                                 String correo, String proveedor,
                                                 @Nullable String fotoUrl) {
        Map<String, Object> apartado = new HashMap<>();
        apartado.put("uid", yo.getUid());
        apartado.put("correo", correo);

        Map<String, Object> perfil = new HashMap<>();
        perfil.put("nombre", nombre);
        perfil.put("username", usuario);
        perfil.put("correo", correo);
        perfil.put("proveedor", proveedor);
        perfil.put("fotoUrl", fotoUrl);
        perfil.put("avatar", null);
        perfil.put("universidadId", null);
        perfil.put("recordarNotas", false);
        perfil.put("alertaRiesgo", false);
        perfil.put("creadoEn", FieldValue.serverTimestamp());

        WriteBatch lote = db().batch();
        lote.set(db().collection(USERNAMES).document(usuario), apartado);
        lote.set(db().collection(USUARIOS).document(yo.getUid()), perfil);
        return lote.commit();
    }

    private static boolean esPermisoNegado(@Nullable Exception error) {
        return error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }

    private static String texto(DocumentSnapshot doc, String campo) {
        String valor = doc.getString(campo);
        return valor == null ? "" : valor;
    }

    private static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }
}
