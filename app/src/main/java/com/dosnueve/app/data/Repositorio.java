package com.dosnueve.app.data;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.data.modelo.Semestre;
import com.dosnueve.app.domain.CalculadoraNotas;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dónde viven los datos de 2.9 mientras llega Firebase (Fase 3): un archivo JSON en el
 * almacenamiento interno de la app, así que lo registrado sigue ahí al cerrarla.
 *
 * <p>Las pantallas solo hablan con esta clase. Al pasar a Firestore se cambia esta y nada
 * más. Lo que se calcula no se guarda (RN-12): aquí hay materias, notas, la forma de
 * calificar, los semestres terminados, las fechas del semestre y la cuenta.
 *
 * <p>Cada cambio se guarda en el archivo y se avisa por {@link #cambios()}, para que la
 * pantalla que esté a la vista se vuelva a pintar (por ejemplo, al tocar "Deshacer").
 */
public final class Repositorio {

    private static final String ARCHIVO = "datos.json";
    private static final String ETIQUETA = "Repositorio";
    /** Semanas de un semestre mientras no se pongan las fechas (doc 02 · 1). */
    private static final int SEMANAS_POR_DEFECTO = 16;
    /** Forma de calificar predeterminada: tres cortes de 33, 33 y 34 % (doc 02 · 1). */
    private static final double[] CORTES_POR_DEFECTO = {33, 33, 34};

    private static Repositorio instancia;

    private final File archivo;
    private final MutableLiveData<Integer> cambios = new MutableLiveData<>(0);

    private String nombre = "";
    private String correo = "";
    private String universidad = "";
    private boolean recordarNotas;
    private boolean avisarRiesgo;
    private LocalDate inicioSemestre;
    private LocalDate finSemestre;
    private List<Corte> formaDeCalificar = formaPorDefecto(CORTES_POR_DEFECTO);
    private double notaParaPasar = 3.0;
    private final List<Materia> materias = new ArrayList<>();
    private final Map<String, List<Nota>> notas = new HashMap<>();
    private final List<Semestre> anteriores = new ArrayList<>();
    private long siguienteId = 1;

    /** Se llama una vez, al arrancar la app. */
    public static void iniciar(Context contexto) {
        if (instancia == null) {
            instancia = new Repositorio(contexto.getApplicationContext());
        }
    }

    public static Repositorio get() {
        if (instancia == null) {
            throw new IllegalStateException("Falta Repositorio.iniciar() en App");
        }
        return instancia;
    }

    private Repositorio(Context contexto) {
        archivo = new File(contexto.getFilesDir(), ARCHIVO);
        cargar();
        if (inicioSemestre == null || finSemestre == null) {
            // Se fijan una sola vez: si se calcularan cada día, la semana nunca avanzaría
            inicioSemestre = LocalDate.now().minusWeeks(6);
            finSemestre = inicioSemestre.plusWeeks(SEMANAS_POR_DEFECTO);
            guardar();
        }
    }

    /** Cambia cada vez que algo se guarda. Las pantallas lo observan para repintarse. */
    public LiveData<Integer> cambios() {
        return cambios;
    }

    // ===== Cuenta =====

    public String nombre() {
        return nombre;
    }

    public String correo() {
        return correo;
    }

    public String universidad() {
        return universidad;
    }

    /**
     * Nombre y correo de la cuenta, que vienen de Firebase al entrar (ver {@link Cuenta}).
     * La sesión ya no se guarda aquí: la lleva Firebase Auth.
     */
    public void guardarCuenta(String suNombre, String suCorreo) {
        nombre = suNombre.trim();
        correo = suCorreo.trim();
        guardarYAvisar();
    }

    /** ¿Dónde estudias? → Listo: su forma de calificar pasa a ser la plantilla (RF-03). */
    public void elegirUniversidad(String suNombre, double[] cortes, double paraPasar) {
        universidad = suNombre;
        formaDeCalificar = formaPorDefecto(cortes);
        notaParaPasar = paraPasar;
        guardarYAvisar();
    }

    /** ¿Dónde estudias? → Saltar por ahora: queda la forma de calificar que haya. */
    public void empezarSinUniversidad() {
        guardarYAvisar();
    }

    public boolean recordarNotas() {
        return recordarNotas;
    }

    public boolean avisarRiesgo() {
        return avisarRiesgo;
    }

    public void guardarAvisos(boolean recordar, boolean riesgo) {
        recordarNotas = recordar;
        avisarRiesgo = riesgo;
        guardar();
    }

    // ===== Fechas del semestre (RF-27) =====

    public LocalDate inicioSemestre() {
        return inicioSemestre;
    }

    public LocalDate finSemestre() {
        return finSemestre;
    }

    public void guardarFechas(LocalDate inicio, LocalDate fin) {
        inicioSemestre = inicio;
        finSemestre = fin;
        guardarYAvisar();
    }

    public int semanasTotales() {
        return semanasEntre(inicioSemestre, finSemestre);
    }

    /** En qué semana va hoy, entre 1 y el total. */
    public int semanaActual() {
        return Math.max(1, Math.min(semanasTotales(), semanasEntre(inicioSemestre, LocalDate.now())));
    }

    /** "2026-2": el año en que empezó y 1 o 2 según el mes. */
    public String periodoActual() {
        return inicioSemestre.getYear() + "-" + (inicioSemestre.getMonthValue() <= 6 ? 1 : 2);
    }

    public static int semanasEntre(LocalDate desde, LocalDate hasta) {
        long dias = ChronoUnit.DAYS.between(desde, hasta);
        return (int) Math.max(1, Math.ceil(dias / 7.0));
    }

    // ===== Forma de calificar (plantilla, RF-06) =====

    /** Una copia: cambiarla no toca la plantilla hasta {@link #guardarFormaDeCalificar}. */
    public List<Corte> formaDeCalificar() {
        return copiar(formaDeCalificar);
    }

    public double notaParaPasar() {
        return notaParaPasar;
    }

    public void guardarFormaDeCalificar(List<Corte> cortes, double paraPasar) {
        formaDeCalificar = copiar(cortes);
        notaParaPasar = paraPasar;
        guardarYAvisar();
    }

    /** Parcial 60, Trabajos y quices 30 y Autoevaluación 10 en cada corte (doc 02 · 1). */
    public static List<Corte> formaPorDefecto(double[] porcentajes) {
        List<Corte> cortes = new ArrayList<>();
        for (int i = 0; i < porcentajes.length; i++) {
            List<Componente> componentes = new ArrayList<>();
            componentes.add(new Componente(CalculadoraNotas.ID_PARCIAL, "Parcial", 60, true));
            componentes.add(new Componente(CalculadoraNotas.ID_TRABAJOS, "Trabajos y quices", 30, true));
            componentes.add(new Componente(CalculadoraNotas.ID_AUTOEVALUACION, "Autoevaluación", 10, true));
            cortes.add(new Corte(i + 1, porcentajes[i], componentes));
        }
        return cortes;
    }

    public static List<Corte> copiar(List<Corte> cortes) {
        List<Corte> copia = new ArrayList<>();
        for (Corte corte : cortes) {
            List<Componente> componentes = new ArrayList<>();
            for (Componente c : corte.componentes) {
                componentes.add(new Componente(c.id, c.nombre, c.porcentaje, c.promedioIgual));
            }
            copia.add(new Corte(corte.numero, corte.porcentaje, componentes));
        }
        return copia;
    }

    // ===== Materias del semestre en curso =====

    public List<Materia> materias() {
        return Collections.unmodifiableList(materias);
    }

    @Nullable
    public Materia materia(String id) {
        for (Materia materia : materias) {
            if (materia.id.equals(id)) {
                return materia;
            }
        }
        return null;
    }

    /** La materia copia la forma de calificar al crearse (RN-05). */
    public Materia crearMateria(String suNombre, @Nullable String profesor, int creditos) {
        Materia materia = new Materia();
        materia.id = nuevoId();
        materia.nombre = suNombre;
        materia.profesor = profesor;
        materia.creditos = creditos;
        materia.notaParaPasar = notaParaPasar;
        materia.cortes = copiar(formaDeCalificar);
        materias.add(materia);
        notas.put(materia.id, new ArrayList<>());
        guardarYAvisar();
        return materia;
    }

    public void editarMateria(String id, String suNombre, @Nullable String profesor, int creditos) {
        Materia materia = materia(id);
        if (materia == null) {
            return;
        }
        materia.nombre = suNombre;
        materia.profesor = profesor;
        materia.creditos = creditos;
        guardarYAvisar();
    }

    /** Cambia la forma de calificar de una sola materia, sin tocar la plantilla. */
    public void guardarFormaDeMateria(String id, List<Corte> cortes, double paraPasar) {
        Materia materia = materia(id);
        if (materia == null) {
            return;
        }
        materia.cortes = copiar(cortes);
        materia.notaParaPasar = paraPasar;
        guardarYAvisar();
    }

    /** Se borran también sus notas (RF-12). */
    public void borrarMateria(String id) {
        for (int i = materias.size() - 1; i >= 0; i--) {
            if (materias.get(i).id.equals(id)) {
                materias.remove(i);
            }
        }
        notas.remove(id);
        guardarYAvisar();
    }

    // ===== Notas =====

    public List<Nota> notas(String materiaId) {
        List<Nota> lista = notas.get(materiaId);
        return lista == null ? Collections.emptyList() : Collections.unmodifiableList(lista);
    }

    @Nullable
    public Nota nota(String materiaId, String notaId) {
        for (Nota nota : notas(materiaId)) {
            if (nota.id.equals(notaId)) {
                return nota;
            }
        }
        return null;
    }

    public Nota agregarNota(String materiaId, Nota nota) {
        nota.id = nuevoId();
        listaDe(materiaId).add(nota);
        guardarYAvisar();
        return nota;
    }

    /** Reemplaza la nota que tenga el mismo id. */
    public void editarNota(String materiaId, Nota cambiada) {
        List<Nota> lista = listaDe(materiaId);
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).id.equals(cambiada.id)) {
                lista.set(i, cambiada);
            }
        }
        guardarYAvisar();
    }

    /** @return la nota borrada, para poder deshacer con {@link #restaurarNota}. */
    @Nullable
    public Nota borrarNota(String materiaId, String notaId) {
        List<Nota> lista = listaDe(materiaId);
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).id.equals(notaId)) {
                Nota borrada = lista.remove(i);
                guardarYAvisar();
                return borrada;
            }
        }
        return null;
    }

    /** Deshacer: vuelve la nota con su mismo id, si la materia todavía existe. */
    public void restaurarNota(String materiaId, Nota nota) {
        if (materia(materiaId) == null) {
            return;
        }
        listaDe(materiaId).add(nota);
        guardarYAvisar();
    }

    /**
     * Cuánto vale una nota "Con porcentajes". Se guarda sin avisar: la pantalla del corte
     * ya se repinta sola mientras se escribe, y repintarla toda le quitaría el foco al campo.
     */
    public void cambiarPeso(String materiaId, String notaId, @Nullable Double peso) {
        Nota nota = nota(materiaId, notaId);
        if (nota != null) {
            nota.peso = peso;
            guardar();
        }
    }

    /** "Todas valen igual" ↔ "Con porcentajes" de un componente (RF-17). */
    public void cambiarModo(String materiaId, int numeroCorte, String componenteId, boolean promedioIgual) {
        Componente componente = componente(materia(materiaId), numeroCorte, componenteId);
        if (componente != null) {
            componente.promedioIgual = promedioIgual;
            guardarYAvisar();
        }
    }

    @Nullable
    public static Corte corte(@Nullable Materia materia, int numero) {
        if (materia == null) {
            return null;
        }
        for (Corte corte : materia.cortes) {
            if (corte.numero == numero) {
                return corte;
            }
        }
        return null;
    }

    @Nullable
    public static Componente componente(@Nullable Materia materia, int numeroCorte, String componenteId) {
        Corte corte = corte(materia, numeroCorte);
        if (corte == null) {
            return null;
        }
        for (Componente componente : corte.componentes) {
            if (componente.id.equals(componenteId)) {
                return componente;
            }
        }
        return null;
    }

    private List<Nota> listaDe(String materiaId) {
        List<Nota> lista = notas.get(materiaId);
        if (lista == null) {
            lista = new ArrayList<>();
            notas.put(materiaId, lista);
        }
        return lista;
    }

    // ===== Semestres terminados (RF-25) =====

    /** Ordenados del más viejo al más nuevo, como la gráfica. */
    public List<Semestre> anteriores() {
        List<Semestre> lista = new ArrayList<>(anteriores);
        lista.sort(Comparator.comparing(semestre -> semestre.periodo));
        return lista;
    }

    @Nullable
    public Semestre anterior(String id) {
        for (Semestre semestre : anteriores) {
            if (semestre.id.equals(id)) {
                return semestre;
            }
        }
        return null;
    }

    /** Crea el semestre si no tiene id; si lo tiene, lo reemplaza. */
    public void guardarAnterior(Semestre semestre) {
        if (semestre.id == null) {
            semestre.id = nuevoId();
            anteriores.add(semestre);
        } else {
            for (int i = 0; i < anteriores.size(); i++) {
                if (anteriores.get(i).id.equals(semestre.id)) {
                    anteriores.set(i, semestre);
                }
            }
        }
        guardarYAvisar();
    }

    // ===== Archivo =====

    private String nuevoId() {
        return String.valueOf(siguienteId++);
    }

    private void guardarYAvisar() {
        guardar();
        Integer actual = cambios.getValue();
        int siguiente = actual == null ? 1 : actual + 1;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cambios.setValue(siguiente);
        } else {
            cambios.postValue(siguiente);
        }
    }

    /** Se escribe en un archivo aparte y se renombra: si la app se cierra a mitad, no se pierde nada. */
    private void guardar() {
        File temporal = new File(archivo.getPath() + ".tmp");
        try (FileOutputStream salida = new FileOutputStream(temporal)) {
            salida.write(aJson().toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | JSONException error) {
            Log.e(ETIQUETA, "No se pudieron guardar los datos", error);
            return;
        }
        if (!temporal.renameTo(archivo)) {
            Log.e(ETIQUETA, "No se pudo reemplazar " + archivo);
        }
    }

    private void cargar() {
        if (!archivo.exists()) {
            return;
        }
        try (InputStream entrada = new FileInputStream(archivo)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] bloque = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(bloque)) != -1) {
                bytes.write(bloque, 0, leidos);
            }
            deJson(new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())));
        } catch (IOException | JSONException error) {
            // Un archivo dañado no debe tumbar la app: arranca vacía
            Log.e(ETIQUETA, "No se pudieron leer los datos", error);
        }
    }

    private JSONObject aJson() throws JSONException {
        JSONObject raiz = new JSONObject();
        raiz.put("version", 1);
        raiz.put("siguienteId", siguienteId);

        JSONObject cuenta = new JSONObject();
        cuenta.put("nombre", nombre);
        cuenta.put("correo", correo);
        cuenta.put("universidad", universidad);
        cuenta.put("recordarNotas", recordarNotas);
        cuenta.put("avisarRiesgo", avisarRiesgo);
        raiz.put("cuenta", cuenta);

        if (inicioSemestre != null && finSemestre != null) {
            raiz.put("inicioSemestre", inicioSemestre.toString());
            raiz.put("finSemestre", finSemestre.toString());
        }
        raiz.put("notaParaPasar", notaParaPasar);
        raiz.put("formaDeCalificar", cortesAJson(formaDeCalificar));

        JSONArray lasMaterias = new JSONArray();
        for (Materia materia : materias) {
            JSONObject json = materiaAJson(materia);
            JSONArray susNotas = new JSONArray();
            for (Nota nota : notas(materia.id)) {
                susNotas.put(notaAJson(nota));
            }
            json.put("notas", susNotas);
            lasMaterias.put(json);
        }
        raiz.put("materias", lasMaterias);

        JSONArray losSemestres = new JSONArray();
        for (Semestre semestre : anteriores) {
            JSONObject json = new JSONObject();
            json.put("id", semestre.id);
            json.put("periodo", semestre.periodo);
            JSONArray susMaterias = new JSONArray();
            for (Materia materia : semestre.materias) {
                susMaterias.put(materiaAJson(materia));
            }
            json.put("materias", susMaterias);
            losSemestres.put(json);
        }
        raiz.put("semestresAnteriores", losSemestres);
        return raiz;
    }

    private void deJson(JSONObject raiz) throws JSONException {
        siguienteId = raiz.optLong("siguienteId", 1);

        JSONObject cuenta = raiz.optJSONObject("cuenta");
        if (cuenta != null) {
            nombre = cuenta.optString("nombre", "");
            correo = cuenta.optString("correo", "");
            universidad = cuenta.optString("universidad", "");
            recordarNotas = cuenta.optBoolean("recordarNotas", false);
            avisarRiesgo = cuenta.optBoolean("avisarRiesgo", false);
        }

        if (raiz.has("inicioSemestre") && raiz.has("finSemestre")) {
            inicioSemestre = LocalDate.parse(raiz.getString("inicioSemestre"));
            finSemestre = LocalDate.parse(raiz.getString("finSemestre"));
        }
        notaParaPasar = raiz.optDouble("notaParaPasar", 3.0);
        JSONArray forma = raiz.optJSONArray("formaDeCalificar");
        if (forma != null && forma.length() > 0) {
            formaDeCalificar = cortesDeJson(forma);
        }

        JSONArray lasMaterias = raiz.optJSONArray("materias");
        if (lasMaterias != null) {
            for (int i = 0; i < lasMaterias.length(); i++) {
                JSONObject json = lasMaterias.getJSONObject(i);
                Materia materia = materiaDeJson(json);
                materias.add(materia);
                List<Nota> susNotas = new ArrayList<>();
                JSONArray lista = json.optJSONArray("notas");
                if (lista != null) {
                    for (int j = 0; j < lista.length(); j++) {
                        susNotas.add(notaDeJson(lista.getJSONObject(j)));
                    }
                }
                notas.put(materia.id, susNotas);
            }
        }

        JSONArray losSemestres = raiz.optJSONArray("semestresAnteriores");
        if (losSemestres != null) {
            for (int i = 0; i < losSemestres.length(); i++) {
                JSONObject json = losSemestres.getJSONObject(i);
                Semestre semestre = new Semestre();
                semestre.id = json.getString("id");
                semestre.periodo = json.getString("periodo");
                JSONArray susMaterias = json.optJSONArray("materias");
                if (susMaterias != null) {
                    for (int j = 0; j < susMaterias.length(); j++) {
                        semestre.materias.add(materiaDeJson(susMaterias.getJSONObject(j)));
                    }
                }
                anteriores.add(semestre);
            }
        }
    }

    private static JSONObject materiaAJson(Materia materia) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", materia.id);
        json.put("nombre", materia.nombre);
        if (materia.profesor != null) {
            json.put("profesor", materia.profesor);
        }
        json.put("creditos", materia.creditos);
        json.put("notaParaPasar", materia.notaParaPasar);
        if (materia.notaFinal != null) {
            json.put("notaFinal", materia.notaFinal);
        }
        json.put("cortes", cortesAJson(materia.cortes));
        return json;
    }

    private static Materia materiaDeJson(JSONObject json) throws JSONException {
        Materia materia = new Materia();
        materia.id = json.optString("id", "");
        materia.nombre = json.optString("nombre", "");
        materia.profesor = json.has("profesor") ? json.getString("profesor") : null;
        materia.creditos = json.optInt("creditos", 0);
        materia.notaParaPasar = json.optDouble("notaParaPasar", 3.0);
        materia.notaFinal = json.has("notaFinal") ? json.getDouble("notaFinal") : null;
        JSONArray cortes = json.optJSONArray("cortes");
        materia.cortes = cortes == null ? new ArrayList<>() : cortesDeJson(cortes);
        return materia;
    }

    private static JSONArray cortesAJson(List<Corte> cortes) throws JSONException {
        JSONArray lista = new JSONArray();
        for (Corte corte : cortes) {
            JSONObject json = new JSONObject();
            json.put("numero", corte.numero);
            json.put("porcentaje", corte.porcentaje);
            JSONArray componentes = new JSONArray();
            for (Componente componente : corte.componentes) {
                JSONObject c = new JSONObject();
                c.put("id", componente.id);
                c.put("nombre", componente.nombre);
                c.put("porcentaje", componente.porcentaje);
                c.put("promedioIgual", componente.promedioIgual);
                componentes.put(c);
            }
            json.put("componentes", componentes);
            lista.put(json);
        }
        return lista;
    }

    private static List<Corte> cortesDeJson(JSONArray lista) throws JSONException {
        List<Corte> cortes = new ArrayList<>();
        for (int i = 0; i < lista.length(); i++) {
            JSONObject json = lista.getJSONObject(i);
            List<Componente> componentes = new ArrayList<>();
            JSONArray susComponentes = json.optJSONArray("componentes");
            if (susComponentes != null) {
                for (int j = 0; j < susComponentes.length(); j++) {
                    JSONObject c = susComponentes.getJSONObject(j);
                    componentes.add(new Componente(c.getString("id"), c.optString("nombre", ""),
                            c.optDouble("porcentaje", 0), c.optBoolean("promedioIgual", true)));
                }
            }
            cortes.add(new Corte(json.getInt("numero"), json.optDouble("porcentaje", 0), componentes));
        }
        return cortes;
    }

    private static JSONObject notaAJson(Nota nota) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", nota.id);
        json.put("corte", nota.corte);
        json.put("componenteId", nota.componenteId);
        if (nota.nombre != null) {
            json.put("nombre", nota.nombre);
        }
        json.put("valor", nota.valor);
        if (nota.peso != null) {
            json.put("peso", nota.peso);
        }
        if (nota.fecha != null) {
            json.put("fecha", nota.fecha);
        }
        return json;
    }

    private static Nota notaDeJson(JSONObject json) throws JSONException {
        Nota nota = new Nota(json.getInt("corte"), json.getString("componenteId"), json.getDouble("valor"),
                json.has("peso") ? json.getDouble("peso") : null);
        nota.id = json.getString("id");
        nota.nombre = json.has("nombre") ? json.getString("nombre") : null;
        nota.fecha = json.has("fecha") ? json.getString("fecha") : null;
        return nota;
    }
}
