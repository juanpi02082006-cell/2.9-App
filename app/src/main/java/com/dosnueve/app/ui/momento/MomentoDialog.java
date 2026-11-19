package com.dosnueve.app.ui.momento;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;

import com.dosnueve.app.MainActivity;
import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.domain.Momento;
import com.dosnueve.app.util.Formato;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * El momento de cerrar un corte o el semestre: felicita según la nota o, si no pasó,
 * acompaña con el plan concreto (cuánto necesita en lo que falta). Ver {@link Momento}.
 */
public class MomentoDialog extends DialogFragment {

    public static final String ETIQUETA = "momento";
    private static final String ARG_MOMENTO = "momento";
    private static final Locale ESPANOL = new Locale("es", "CO");

    private View velo;
    private View tarjeta;
    private boolean cerrando;

    public static MomentoDialog de(Momento momento) {
        Bundle argumentos = new Bundle();
        argumentos.putSerializable(ARG_MOMENTO, momento);
        MomentoDialog dialogo = new MomentoDialog();
        dialogo.setArguments(argumentos);
        return dialogo;
    }

    @Override
    public void onCreate(@Nullable Bundle estado) {
        super.onCreate(estado);
        // Sin título; el fondo y el velo los pone la propia vista
        setStyle(STYLE_NO_TITLE, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.dialogo_momento, padre, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialogo = getDialog();
        Window ventana = dialogo == null ? null : dialogo.getWindow();
        if (ventana != null) {
            ventana.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ventana.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            ventana.setDimAmount(0f);
        }
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        Momento momento = BundleCompat.getSerializable(requireArguments(), ARG_MOMENTO, Momento.class);
        if (momento == null) {
            dismissAllowingStateLoss();
            return;
        }
        velo = vista.findViewById(R.id.velo);
        tarjeta = vista.findViewById(R.id.tarjeta);
        TextView nota = vista.findViewById(R.id.nota);
        TextView sello = vista.findViewById(R.id.sello);
        ConfetiView confeti = vista.findViewById(R.id.confeti);

        llenar(vista, momento);
        pintarSello(sello, momento.tipo);
        if (!momento.tipo.celebra()) {
            // Sin pasar: azul tranquilo, nada de fiesta
            tarjeta.setBackgroundTintList(ColorStateList.valueOf(color(R.color.azul_suave)));
        }

        velo.setOnClickListener(v -> cerrar());
        vista.findViewById(R.id.botonSeguir).setOnClickListener(v -> cerrar());

        // Todo arranca escondido y entra en orden cuando ya se sabe cuánto mide
        nota.setText(Formato.nota(0.0));
        sello.setAlpha(0f);
        tarjeta.setVisibility(View.INVISIBLE);
        vista.post(() -> animarEntrada(nota, sello, confeti, momento));
    }

    private void llenar(View vista, Momento momento) {
        TextView etiqueta = vista.findViewById(R.id.etiqueta);
        TextView mensaje = vista.findViewById(R.id.mensaje);
        TextView plan = vista.findViewById(R.id.plan);
        String nombre = primerNombre(Repositorio.get().nombre());

        if (momento.tipo.esSemestre()) {
            etiqueta.setText(getString(R.string.momento_etiqueta_semestre, momento.periodo));
            TextView aprobadas = vista.findViewById(R.id.aprobadas);
            aprobadas.setText(getString(R.string.momento_aprobadas, momento.aprobadas, momento.total));
            aprobadas.setVisibility(View.VISIBLE);
            if (momento.mejorNota >= 0 && !momento.mejorMateria.isEmpty()) {
                TextView mejor = vista.findViewById(R.id.mejor);
                mejor.setText(getString(R.string.momento_mejor, momento.mejorMateria,
                        Formato.nota(momento.mejorNota)));
                mejor.setVisibility(View.VISIBLE);
            }
            mensaje.setText(escoger(mensajesDe(momento.tipo), nombre, Formato.nota(momento.nota),
                    momento.semanas, momento.aprobadas, momento.total));
            plan.setVisibility(View.GONE);
            return;
        }

        etiqueta.setText(getString(R.string.momento_etiqueta_corte, momento.corte, momento.materia));
        String necesitas = momento.necesitas == null ? "" : Formato.nota(momento.necesitas);
        mensaje.setText(escoger(mensajesDe(momento.tipo), nombre, momento.materia,
                Formato.nota(momento.nota), momento.corte, necesitas));

        // La pastilla: el plan si no pasó; si pasó, cómo va la materia
        String textoPlan = null;
        if (momento.tipo == Momento.Tipo.NO_PASO) {
            textoPlan = getString(R.string.momento_plan_necesitas, necesitas);
        } else if (momento.definitiva != null) {
            textoPlan = getString(R.string.momento_plan_definitiva, Formato.nota(momento.definitiva));
        } else if (momento.llevas != null && momento.tipo.celebra()) {
            textoPlan = getString(R.string.momento_plan_llevas, Formato.nota(momento.llevas));
        }
        if (textoPlan == null) {
            plan.setVisibility(View.GONE);
        } else {
            plan.setText(textoPlan);
            GradientDrawable pastilla = new GradientDrawable();
            pastilla.setCornerRadius(999);
            pastilla.setColor(color(momento.tipo.celebra() ? R.color.aprobado_suave : R.color.superficie));
            plan.setBackground(pastilla);
        }
    }

    // ===== Textos =====

    @ArrayRes
    private static int mensajesDe(Momento.Tipo tipo) {
        switch (tipo) {
            case DE_LUJO:
                return R.array.momento_de_lujo;
            case MUY_BIEN:
                return R.array.momento_muy_bien;
            case BIEN:
                return R.array.momento_bien;
            case RASPANDO:
                return R.array.momento_raspando;
            case NO_PASO:
                return R.array.momento_no_paso;
            case LA_SACASTE:
                return R.array.momento_la_sacaste;
            case SEMESTRE_EXCELENTE:
                return R.array.momento_semestre_excelente;
            case SEMESTRE_TODO_ADENTRO:
                return R.array.momento_semestre_todo_adentro;
            case SEMESTRE_CON_PERDIDAS:
                return R.array.momento_semestre_con_perdidas;
            default:
                return R.array.momento_ya_no_alcanza;
        }
    }

    /** Uno al azar, para que no se repita siempre el mismo. Sin nombre, los que lo usan no. */
    private String escoger(@ArrayRes int cuales, String nombre, Object... datos) {
        List<String> posibles = new ArrayList<>();
        for (String uno : getResources().getStringArray(cuales)) {
            if (!nombre.isEmpty() || !uno.contains("%1$s")) {
                posibles.add(uno);
            }
        }
        String elegido = posibles.get(new Random().nextInt(posibles.size()));
        Object[] todo = new Object[datos.length + 1];
        todo[0] = nombre;
        System.arraycopy(datos, 0, todo, 1, datos.length);
        return String.format(ESPANOL, elegido, todo);
    }

    private static String primerNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        int espacio = limpio.indexOf(' ');
        return espacio < 0 ? limpio : limpio.substring(0, espacio);
    }

    // ===== El sello =====

    private void pintarSello(TextView sello, Momento.Tipo tipo) {
        @StringRes int texto;
        @ColorRes int tinta;
        switch (tipo) {
            case DE_LUJO:
                texto = R.string.momento_sello_de_lujo;
                tinta = R.color.dorado;
                break;
            case MUY_BIEN:
            case BIEN:
                texto = R.string.momento_sello_aprobado;
                tinta = R.color.aprobado;
                break;
            case RASPANDO:
                texto = R.string.momento_sello_paso;
                tinta = R.color.azul_oscuro;
                break;
            case NO_PASO:
                texto = R.string.momento_sello_se_viene;
                tinta = R.color.azul_oscuro;
                break;
            case LA_SACASTE:
                texto = R.string.momento_sello_la_sacaste;
                tinta = R.color.aprobado;
                break;
            case YA_NO_ALCANZA:
                texto = R.string.momento_sello_otra_vuelta;
                tinta = R.color.azul_oscuro;
                break;
            case SEMESTRE_EXCELENTE:
                texto = R.string.momento_sello_cerrado;
                tinta = R.color.dorado;
                break;
            case SEMESTRE_TODO_ADENTRO:
                texto = R.string.momento_sello_cerrado;
                tinta = R.color.aprobado;
                break;
            default:
                texto = R.string.momento_sello_cerrado;
                tinta = R.color.azul_oscuro;
                break;
        }
        int suColor = color(tinta);
        sello.setText(texto);
        sello.setTextColor(suColor);
        GradientDrawable marco = new GradientDrawable();
        marco.setCornerRadius(8 * densidad());
        marco.setStroke(Math.round(3 * densidad()), suColor);
        // Un poquito de tinta adentro, como sello de verdad
        marco.setColor((suColor & 0x00FFFFFF) | 0x1A000000);
        sello.setBackground(marco);
    }

    // ===== Animaciones =====

    private void animarEntrada(TextView nota, TextView sello, ConfetiView confeti, Momento momento) {
        if (!isAdded()) {
            return;
        }
        float abajo = tarjeta.getHeight() + 80 * densidad();
        tarjeta.setTranslationY(abajo);
        tarjeta.setVisibility(View.VISIBLE);
        velo.animate().alpha(1f).setDuration(250).start();
        tarjeta.animate().translationY(0).setDuration(480)
                .setInterpolator(new DecelerateInterpolator(2f)).start();

        // La nota cuenta desde 0.0 hasta la de verdad
        ValueAnimator cuenta = ValueAnimator.ofFloat(0f, (float) momento.nota);
        cuenta.setStartDelay(300);
        cuenta.setDuration(800);
        cuenta.setInterpolator(new DecelerateInterpolator(1.6f));
        cuenta.addUpdateListener(paso ->
                nota.setText(Formato.nota((double) (float) paso.getAnimatedValue())));
        cuenta.start();

        // El sello cae grande y girado, y golpea
        sello.setScaleX(2.8f);
        sello.setScaleY(2.8f);
        sello.setRotation(-32f);
        sello.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(-12f)
                .setStartDelay(1150).setDuration(230)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .withEndAction(() -> golpe(sello, confeti, momento.tipo))
                .start();
    }

    private void golpe(TextView sello, ConfetiView confeti, Momento.Tipo tipo) {
        if (!isAdded()) {
            return;
        }
        tarjeta.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
        // La tarjeta se hunde un poquito con el golpe
        tarjeta.animate().translationY(7 * densidad()).setDuration(70).withEndAction(() ->
                tarjeta.animate().translationY(0).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(3f)).start()).start();

        if (!tipo.celebra()) {
            return;
        }
        int[] dondeSello = new int[2];
        int[] dondeConfeti = new int[2];
        sello.getLocationInWindow(dondeSello);
        confeti.getLocationInWindow(dondeConfeti);
        float x = dondeSello[0] - dondeConfeti[0] + sello.getWidth() / 2f;
        float y = dondeSello[1] - dondeConfeti[1] + sello.getHeight() / 2f;

        boolean enGrande = tipo == Momento.Tipo.DE_LUJO || tipo == Momento.Tipo.SEMESTRE_EXCELENTE;
        int[] colores = enGrande
                ? new int[]{color(R.color.dorado), color(R.color.dorado), color(R.color.azul),
                color(R.color.riesgo_vivo), color(R.color.verde_menta), color(R.color.azul_suave)}
                : new int[]{color(R.color.azul), color(R.color.riesgo_vivo), color(R.color.verde_menta),
                color(R.color.azul_suave), color(R.color.azul_oscuro)};
        confeti.estallar(x, y, enGrande ? 140 : 90, colores);
        if (enGrande) {
            confeti.postDelayed(() -> confeti.llover(110, colores), 350);
        }
    }

    private void cerrar() {
        if (cerrando) {
            return;
        }
        cerrando = true;
        velo.animate().alpha(0f).setDuration(220).start();
        tarjeta.animate().translationY(tarjeta.getHeight() + 80 * densidad()).setDuration(260)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .withEndAction(this::dismissAllowingStateLoss)
                .start();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialogo) {
        super.onDismiss(dialogo);
        // Si quedó otro momento en fila (por ejemplo, el del semestre), sale después
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).mostrarMomentoLuego();
        }
    }

    private int color(@ColorRes int cual) {
        return ContextCompat.getColor(requireContext(), cual);
    }

    private float densidad() {
        return getResources().getDisplayMetrics().density;
    }
}
