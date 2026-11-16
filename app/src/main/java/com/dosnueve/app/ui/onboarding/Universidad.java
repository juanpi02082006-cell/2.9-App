package com.dosnueve.app.ui.onboarding;

import com.dosnueve.app.util.Formato;

/** Una universidad de la lista de la pantalla 02, con su forma de calificar. */
public class Universidad {

    public final long id;
    public final String nombre;
    /** Porcentaje de cada corte. */
    public final double[] cortes;
    public final double notaParaPasar;
    /** Resumen para la lista: "3 cortes · 33/33/34 · pasa con 3.0". */
    public final String formaCalificar;

    public Universidad(long id, String nombre, double[] cortes, double notaParaPasar) {
        this.id = id;
        this.nombre = nombre;
        this.cortes = cortes;
        this.notaParaPasar = notaParaPasar;

        StringBuilder porcentajes = new StringBuilder();
        for (int i = 0; i < cortes.length; i++) {
            if (i > 0) {
                porcentajes.append('/');
            }
            porcentajes.append(Formato.porcentaje(cortes[i]));
        }
        this.formaCalificar = cortes.length + " cortes · " + porcentajes
                + " · pasa con " + Formato.nota(notaParaPasar);
    }
}
