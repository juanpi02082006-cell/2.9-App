package com.dosnueve.app.ui.perfil;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Cuenta;
import com.dosnueve.app.data.Repositorio;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Elige tu foto (Perfil): la foto de Google, uno de los 8 avatares o las iniciales.
 *
 * <p>Si la cuenta no tiene foto de Google (se creó con correo), el círculo de Google abre
 * el selector de cuentas y la conecta; desde ahí se ve su foto.
 */
public class AvatarBottomSheet extends BottomSheetDialogFragment {

    public static final String ETIQUETA = "avatar";
    private static final int POR_FILA = 5;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.hoja_avatar, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        Repositorio datos = Repositorio.get();
        String escogido = Avatar.queMostrar(datos.avatar(), datos.fotoUrl());
        String iniciales = Avatar.inicialesDe(datos.nombre().isEmpty() ? datos.correo() : datos.nombre());

        List<View> circulos = new ArrayList<>();
        LinearLayout opciones = vista.findViewById(R.id.opciones);

        // 1. Foto de Google: la suya si ya la hay; si no, el logo de Google para conectarla
        View google = crear(opciones, R.string.avatar_google, Avatar.FOTO.equals(escogido));
        ImageView fotoGoogle = google.findViewById(R.id.foto);
        if (datos.fotoUrl() != null) {
            Avatar.pintar(fotoGoogle, google.findViewById(R.id.iniciales), Avatar.FOTO,
                    datos.fotoUrl(), iniciales);
        } else {
            google.findViewById(R.id.iniciales).setVisibility(View.GONE);
            GradientDrawable circulo = new GradientDrawable();
            circulo.setShape(GradientDrawable.OVAL);
            circulo.setColor(ContextCompat.getColor(requireContext(), R.color.google_fondo));
            circulo.setStroke(Math.round(getResources().getDisplayMetrics().density),
                    ContextCompat.getColor(requireContext(), R.color.google_borde));
            fotoGoogle.setBackground(circulo);
            fotoGoogle.setImageResource(R.drawable.ic_google);
            int margen = Math.round(56 * getResources().getDisplayMetrics().density * 0.26f);
            fotoGoogle.setPadding(margen, margen, margen, margen);
        }
        google.setOnClickListener(v -> escogerGoogle());
        circulos.add(google);

        // 2. Los 8 avatares
        for (Avatar.Opcion opcion : Avatar.OPCIONES) {
            View uno = crear(opciones, opcion.nombre, opcion.id.equals(escogido));
            Avatar.pintar(uno.findViewById(R.id.foto), uno.findViewById(R.id.iniciales),
                    opcion.id, null, "");
            uno.setOnClickListener(v -> escoger(opcion.id));
            circulos.add(uno);
        }

        // 3. Las iniciales, como estaba antes
        View lasIniciales = crear(opciones, R.string.avatar_iniciales, Avatar.INICIALES.equals(escogido));
        Avatar.pintar(lasIniciales.findViewById(R.id.foto), lasIniciales.findViewById(R.id.iniciales),
                Avatar.INICIALES, null, iniciales.isEmpty() ? "?" : iniciales);
        lasIniciales.setOnClickListener(v -> escoger(Avatar.INICIALES));
        circulos.add(lasIniciales);

        acomodar(opciones, circulos);
    }

    /** Se infla con el contenedor (sin pegarlo) para que conserve sus 56dp. */
    private View crear(ViewGroup contenedor, @StringRes int nombre, boolean escogido) {
        View circulo = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_avatar, contenedor, false);
        circulo.setContentDescription(getString(nombre));
        circulo.findViewById(R.id.anillo).setVisibility(escogido ? View.VISIBLE : View.GONE);
        circulo.setSelected(escogido);
        return circulo;
    }

    /** Filas de cinco, repartidas a lo ancho. */
    private void acomodar(LinearLayout opciones, List<View> circulos) {
        int lado = Math.round(56 * getResources().getDisplayMetrics().density);
        int entreFilas = getResources().getDimensionPixelSize(R.dimen.espacio_l);
        LinearLayout fila = null;
        for (int i = 0; i < circulos.size(); i++) {
            if (i % POR_FILA == 0) {
                fila = new LinearLayout(requireContext());
                fila.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams medidas = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                medidas.topMargin = i == 0 ? 0 : entreFilas;
                opciones.addView(fila, medidas);
            }
            FrameLayout celda = new FrameLayout(requireContext());
            FrameLayout.LayoutParams centrado = new FrameLayout.LayoutParams(lado, lado);
            centrado.gravity = android.view.Gravity.CENTER;
            celda.addView(circulos.get(i), centrado);
            fila.addView(celda, new LinearLayout.LayoutParams(0, lado, 1f));
        }
    }

    private void escoger(String cual) {
        Repositorio.get().guardarAvatar(cual);
        dismiss();
    }

    private void escogerGoogle() {
        if (Repositorio.get().fotoUrl() != null) {
            escoger(Avatar.FOTO);
            return;
        }
        Cuenta.conectarGoogle(requireActivity()).addOnCompleteListener(conexion -> {
            if (!isAdded()) {
                return;
            }
            if (conexion.isSuccessful()) {
                String foto = conexion.getResult();
                if (foto == null) {
                    avisar(R.string.avatar_sin_foto);
                    return;
                }
                Repositorio.get().guardarFotoDeGoogle(foto);
                dismiss();
                return;
            }
            switch (Cuenta.queFallo(conexion.getException())) {
                case CANCELADO:
                    break;
                case SIN_INTERNET:
                    avisar(R.string.error_sin_internet_corto);
                    break;
                case SIN_CUENTA_GOOGLE:
                    avisar(R.string.error_google_sin_cuenta);
                    break;
                default:
                    avisar(R.string.error_algo_fallo);
                    break;
            }
        });
    }

    private void avisar(@StringRes int mensaje) {
        Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show();
    }
}
