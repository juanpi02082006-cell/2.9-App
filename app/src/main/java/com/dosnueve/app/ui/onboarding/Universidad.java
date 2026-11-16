package com.dosnueve.app.ui.onboarding;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.dosnueve.app.util.Formato;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Una institución de la lista de la pantalla 02.
 *
 * <p>La lista son las instituciones de educación superior de Colombia (sedes principales)
 * del SNIES, de datos abiertos del MinEducación (datos.gov.co, conjunto n5yy-8nav). Va en
 * {@code assets/universidades.json} porque casi no cambia y así el buscador funciona sin
 * Internet. Solo algunas traen su forma de calificar; las demás empiezan con 33/33/34.
 */
public class Universidad {

    private static final String ETIQUETA = "Universidad";
    private static final String ARCHIVO = "universidades.json";
    private static final double[] CORTES_POR_DEFECTO = {33, 33, 34};

    /** Código SNIES de la institución. */
    public final long id;
    public final String nombre;
    public final String ciudad;
    /** Porcentaje de cada corte. */
    public final double[] cortes;
    public final double notaParaPasar;
    /** Segunda línea de la fila: la forma de calificar si se conoce, si no la ciudad. */
    public final String detalle;
    /** Nombre y ciudad sin tildes y en minúsculas, para buscar. */
    private final String paraBuscar;

    public Universidad(long id, String nombre, String ciudad, @Nullable double[] cortes,
                       double notaParaPasar) {
        this.id = id;
        this.nombre = nombre;
        this.ciudad = ciudad;
        this.cortes = cortes == null ? CORTES_POR_DEFECTO : cortes;
        this.notaParaPasar = notaParaPasar;
        this.detalle = cortes == null ? ciudad : resumen(cortes, notaParaPasar);
        this.paraBuscar = sinTildes(nombre + " " + ciudad);
    }

    /** "3 cortes · 30/30/40 · pasa con 3.0". */
    private static String resumen(double[] cortes, double notaParaPasar) {
        StringBuilder porcentajes = new StringBuilder();
        for (int i = 0; i < cortes.length; i++) {
            if (i > 0) {
                porcentajes.append('/');
            }
            porcentajes.append(Formato.porcentaje(cortes[i]));
        }
        return cortes.length + " cortes · " + porcentajes + " · pasa con " + Formato.nota(notaParaPasar);
    }

    /** Sirve si cada palabra buscada aparece en el nombre o la ciudad ("nacional bogota"). */
    public boolean coincide(String busqueda) {
        for (String palabra : sinTildes(busqueda).trim().split("\\s+")) {
            if (!palabra.isEmpty() && !paraBuscar.contains(palabra)) {
                return false;
            }
        }
        return true;
    }

    static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Lee la lista de assets. Si algo falla, la lista sale vacía y se puede saltar. */
    public static List<Universidad> todas(Context contexto) {
        List<Universidad> lista = new ArrayList<>();
        try (InputStream entrada = contexto.getAssets().open(ARCHIVO)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] bloque = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(bloque)) != -1) {
                bytes.write(bloque, 0, leidos);
            }
            JSONArray json = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < json.length(); i++) {
                JSONObject una = json.getJSONObject(i);
                double[] cortes = null;
                JSONArray susCortes = una.optJSONArray("cortes");
                if (susCortes != null) {
                    cortes = new double[susCortes.length()];
                    for (int j = 0; j < cortes.length; j++) {
                        cortes[j] = susCortes.getDouble(j);
                    }
                }
                lista.add(new Universidad(una.getLong("id"), una.getString("nombre"),
                        una.optString("ciudad", ""), cortes, una.optDouble("notaParaPasar", 3.0)));
            }
        } catch (IOException | JSONException error) {
            Log.e(ETIQUETA, "No se pudo leer " + ARCHIVO, error);
        }
        return lista;
    }
}
