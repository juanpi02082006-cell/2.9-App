package com.dosnueve.app.ui.perfil;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;

import java.util.Arrays;
import java.util.List;

/**
 * El círculo de la persona en Perfil y en el Inicio: su foto de Google, un avatar de los
 * 8 (birrete, lápiz…) o sus iniciales. Lo escogido se guarda en el perfil ({@code avatar});
 * sin escoger, sale la foto si la hay y si no las iniciales.
 */
public final class Avatar {

    public static final String FOTO = "foto";
    public static final String INICIALES = "iniciales";

    /** Uno de los 8 avatares: un ícono sobre un círculo de la paleta. */
    public static final class Opcion {
        public final String id;
        @DrawableRes
        public final int icono;
        @ColorRes
        public final int fondo;
        @ColorRes
        public final int tinte;
        @StringRes
        public final int nombre;

        Opcion(String id, @DrawableRes int icono, @ColorRes int fondo, @ColorRes int tinte,
               @StringRes int nombre) {
            this.id = id;
            this.icono = icono;
            this.fondo = fondo;
            this.tinte = tinte;
            this.nombre = nombre;
        }
    }

    public static final List<Opcion> OPCIONES = Arrays.asList(
            new Opcion("birrete", R.drawable.av_birrete, R.color.azul, R.color.blanco, R.string.avatar_birrete),
            new Opcion("lapiz", R.drawable.ic_lapiz, R.color.riesgo_vivo, R.color.blanco, R.string.avatar_lapiz),
            new Opcion("libro", R.drawable.av_libro, R.color.verde_menta, R.color.tinta, R.string.avatar_libro),
            new Opcion("cafe", R.drawable.av_cafe, R.color.azul_suave, R.color.azul_oscuro, R.string.avatar_cafe),
            new Opcion("estrella", R.drawable.av_estrella, R.color.riesgo_suave, R.color.riesgo_vivo, R.string.avatar_estrella),
            new Opcion("avion", R.drawable.av_avion, R.color.azul_oscuro, R.color.blanco, R.string.avatar_avion),
            new Opcion("idea", R.drawable.av_idea, R.color.aprobado_suave, R.color.aprobado, R.string.avatar_idea),
            new Opcion("musica", R.drawable.av_musica, R.color.tinta_elevada, R.color.blanco, R.string.avatar_musica));

    private Avatar() {
    }

    @Nullable
    public static Opcion opcion(@Nullable String id) {
        for (Opcion opcion : OPCIONES) {
            if (opcion.id.equals(id)) {
                return opcion;
            }
        }
        return null;
    }

    /** Lo que se ve: lo escogido o, sin escoger, la foto si la hay y si no las iniciales. */
    public static String queMostrar(@Nullable String avatar, @Nullable String fotoUrl) {
        if (FOTO.equals(avatar) || avatar == null || avatar.isEmpty()) {
            return fotoUrl != null && !fotoUrl.isEmpty() ? FOTO : INICIALES;
        }
        return opcion(avatar) != null ? avatar : INICIALES;
    }

    /** Pinta el círculo con lo que tenga la cuenta abierta. */
    public static void pintar(ImageView foto, TextView iniciales) {
        Repositorio datos = Repositorio.get();
        pintar(foto, iniciales, queMostrar(datos.avatar(), datos.fotoUrl()), datos.fotoUrl(),
                inicialesDe(datos.nombre().isEmpty() ? datos.correo() : datos.nombre()));
    }

    /**
     * @param foto      la imagen, encima de las iniciales: mientras la foto baja, se ven ellas
     * @param iniciales el texto de abajo, con su fondo de círculo
     */
    public static void pintar(ImageView foto, TextView iniciales, String que,
                              @Nullable String fotoUrl, String susIniciales) {
        Context contexto = foto.getContext();
        Glide.with(foto).clear(foto);
        iniciales.setText(susIniciales);
        iniciales.setVisibility(View.VISIBLE);
        foto.setVisibility(View.VISIBLE);
        foto.setImageTintList(null);
        foto.setPadding(0, 0, 0, 0);

        Opcion opcion = opcion(que);
        if (FOTO.equals(que) && fotoUrl != null) {
            foto.setBackground(null);
            foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(foto).load(urlGrande(fotoUrl)).circleCrop().into(foto);
        } else if (opcion != null) {
            GradientDrawable circulo = new GradientDrawable();
            circulo.setShape(GradientDrawable.OVAL);
            circulo.setColor(ContextCompat.getColor(contexto, opcion.fondo));
            foto.setBackground(circulo);
            foto.setScaleType(ImageView.ScaleType.FIT_CENTER);
            foto.setImageResource(opcion.icono);
            foto.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(contexto, opcion.tinte)));
            int margen = Math.round(medida(foto) * 0.22f);
            foto.setPadding(margen, margen, margen, margen);
        } else if (!susIniciales.isEmpty()) {
            foto.setImageDrawable(null);
            foto.setBackground(null);
            foto.setVisibility(View.GONE);
        } else {
            // Sin nombre todavía: la silueta, para que se entienda que ahí está el Perfil
            iniciales.setVisibility(View.GONE);
            foto.setBackgroundResource(R.drawable.fondo_avatar);
            foto.setScaleType(ImageView.ScaleType.FIT_CENTER);
            foto.setImageResource(R.drawable.ic_usuario_linea);
            foto.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(contexto, R.color.acento_oscuro)));
            int margen = Math.round(medida(foto) * 0.2f);
            foto.setPadding(margen, margen, margen, margen);
        }
    }

    /** Google entrega la foto pequeñita (=s96-c); se pide de 256 para que no se vea borrosa. */
    static String urlGrande(String url) {
        return url.replaceAll("=s\\d+(-c)?$", "=s256-c");
    }

    public static String inicialesDe(@Nullable String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return "";
        }
        String[] partes = nombre.trim().split("\\s+");
        StringBuilder iniciales = new StringBuilder();
        for (int i = 0; i < Math.min(2, partes.length); i++) {
            iniciales.append(Character.toUpperCase(partes[i].charAt(0)));
        }
        return iniciales.toString();
    }

    /** El lado del círculo; si todavía no se ha medido, el del padre o 40dp. */
    private static int medida(View vista) {
        int lado = vista.getLayoutParams() == null ? 0 : vista.getLayoutParams().width;
        if (lado <= 0 && vista.getParent() instanceof View
                && ((View) vista.getParent()).getLayoutParams() != null) {
            lado = ((View) vista.getParent()).getLayoutParams().width;
        }
        if (lado <= 0) {
            lado = Math.round(40 * vista.getResources().getDisplayMetrics().density);
        }
        return lado;
    }
}
