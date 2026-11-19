package com.dosnueve.app.ui.momento;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Confeti con los colores de la app. Hecho a mano (sin librerías): cada papelito sale
 * disparado desde un punto (el sello) o cae desde arriba, gira y lo jala la gravedad.
 */
public class ConfetiView extends View {

    private static final class Papelito {
        float x;
        float y;
        float vx;
        float vy;
        float ancho;
        float alto;
        float giro;
        float vGiro;
        float vaiven;
        int color;
        boolean redondo;
    }

    private final List<Papelito> papelitos = new ArrayList<>();
    private final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random azar = new Random();
    private final float dp;
    private long ultimoCuadro;

    public ConfetiView(Context contexto, @Nullable AttributeSet atributos) {
        super(contexto, atributos);
        dp = getResources().getDisplayMetrics().density;
    }

    /** Explosión desde (x, y): sale hacia arriba en abanico y luego cae. */
    public void estallar(float x, float y, int cuantos, int[] colores) {
        for (int i = 0; i < cuantos; i++) {
            Papelito p = nuevo(colores);
            double angulo = Math.toRadians(-160 + azar.nextFloat() * 140);
            float rapidez = (450 + azar.nextFloat() * 650) * dp;
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(angulo) * rapidez;
            p.vy = (float) Math.sin(angulo) * rapidez;
            papelitos.add(p);
        }
        arrancar();
    }

    /** Lluvia desde arriba de la pantalla, para los momentos grandes. */
    public void llover(int cuantos, int[] colores) {
        for (int i = 0; i < cuantos; i++) {
            Papelito p = nuevo(colores);
            p.x = azar.nextFloat() * getWidth();
            p.y = -azar.nextFloat() * getHeight() * 0.6f - 20 * dp;
            p.vx = (azar.nextFloat() - 0.5f) * 80 * dp;
            p.vy = (120 + azar.nextFloat() * 160) * dp;
            papelitos.add(p);
        }
        arrancar();
    }

    private Papelito nuevo(int[] colores) {
        Papelito p = new Papelito();
        p.ancho = (6 + azar.nextFloat() * 5) * dp;
        p.alto = p.ancho * (0.45f + azar.nextFloat() * 1.1f);
        p.giro = azar.nextFloat() * 360;
        p.vGiro = (azar.nextFloat() - 0.5f) * 900;
        p.vaiven = azar.nextFloat() * 6.28f;
        p.color = colores[azar.nextInt(colores.length)];
        p.redondo = azar.nextFloat() < 0.25f;
        return p;
    }

    private void arrancar() {
        ultimoCuadro = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(@NonNull Canvas lienzo) {
        super.onDraw(lienzo);
        if (papelitos.isEmpty()) {
            return;
        }
        long ahora = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (ahora - ultimoCuadro) / 1000f);
        ultimoCuadro = ahora;
        float gravedad = 1100 * dp;

        Iterator<Papelito> cada = papelitos.iterator();
        while (cada.hasNext()) {
            Papelito p = cada.next();
            // El aire frena, la gravedad jala y el papelito se mece al caer
            p.vx *= (1 - 1.6f * dt);
            p.vy = p.vy * (1 - 0.9f * dt) + gravedad * dt;
            p.vaiven += dt * 5;
            p.x += (p.vx + (float) Math.sin(p.vaiven) * 40 * dp) * dt;
            p.y += p.vy * dt;
            p.giro += p.vGiro * dt;
            if (p.y > getHeight() + 40 * dp) {
                cada.remove();
                continue;
            }
            pincel.setColor(p.color);
            lienzo.save();
            lienzo.translate(p.x, p.y);
            lienzo.rotate(p.giro);
            // Al girar en 3D el papelito se "aplana": se simula achicando un lado
            float aplanado = Math.abs((float) Math.cos(Math.toRadians(p.giro * 1.7f)));
            if (p.redondo) {
                lienzo.drawCircle(0, 0, p.ancho / 2 * (0.4f + 0.6f * aplanado), pincel);
            } else {
                float medioAncho = p.ancho / 2;
                float medioAlto = p.alto / 2 * (0.25f + 0.75f * aplanado);
                lienzo.drawRect(-medioAncho, -medioAlto, medioAncho, medioAlto, pincel);
            }
            lienzo.restore();
        }
        if (!papelitos.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }
}
