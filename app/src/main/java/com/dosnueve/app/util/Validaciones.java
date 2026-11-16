package com.dosnueve.app.util;

import java.util.regex.Pattern;

/**
 * Reglas de los campos de las pantallas de cuenta (doc 06 · 4).
 *
 * <p>Sin dependencias de Android, para poder probarlas con pruebas unitarias.
 */
public final class Validaciones {

    /** Usuario: de 3 a 20 caracteres, solo minúsculas, números y guion bajo. */
    private static final Pattern USUARIO = Pattern.compile("^[a-z0-9_]{3,20}$");

    /** Correo: lo mínimo para no dejar pasar basura; el que manda es Firebase. */
    private static final Pattern CORREO =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    public static final int LARGO_MINIMO_CONTRASENA = 8;

    private Validaciones() {
    }

    public static boolean nombreValido(String nombre) {
        return nombre != null && !nombre.trim().isEmpty();
    }

    public static boolean usuarioValido(String usuario) {
        return usuario != null && USUARIO.matcher(usuario.trim()).matches();
    }

    public static boolean correoValido(String correo) {
        return correo != null && CORREO.matcher(correo.trim()).matches();
    }

    public static boolean contrasenaValida(String contrasena) {
        return contrasena != null && contrasena.length() >= LARGO_MINIMO_CONTRASENA;
    }

    /**
     * En la pantalla 02 el usuario escribe correo o nombre de usuario en el mismo
     * campo. Si lleva arroba es un correo; si no, hay que buscar el correo en la
     * colección {@code usernames} antes de entrar (doc 06 · 4).
     */
    public static boolean pareceCorreo(String identificador) {
        return identificador != null && identificador.contains("@");
    }

    /**
     * Usuario sugerido a partir del correo, para las cuentas creadas con Google.
     * Se queda con lo que va antes de la arroba, en minúsculas y sin caracteres
     * raros. El usuario lo puede cambiar después en Perfil.
     */
    public static String usuarioSugerido(String correo) {
        if (correo == null || !correo.contains("@")) {
            return "";
        }
        String limpio = correo.substring(0, correo.indexOf('@'))
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "");
        if (limpio.length() > 20) {
            limpio = limpio.substring(0, 20);
        }
        while (limpio.length() < 3) {
            limpio += "0";
        }
        return limpio;
    }
}
