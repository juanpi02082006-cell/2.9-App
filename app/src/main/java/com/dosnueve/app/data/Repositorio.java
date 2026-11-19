package com.dosnueve.app.data;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.dosnueve.app.avisos.Avisos;
import com.dosnueve.app.avisos.VigiaDeMomentos;
import com.dosnueve.app.avisos.VigiaDeRiesgo;
import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.data.modelo.Semestre;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.domain.Momento;
import androidx.work.ExistingWorkPolicy;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.io.File;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dónde viven los datos de 2.9: Cloud Firestore, bajo {@code usuarios/{uid}} (doc 05).
 *
 * <p>Las pantallas solo hablan con esta clase y no saben de Firestore. Aquí se guarda una
 * copia en memoria de todo lo de la cuenta: las pantallas la leen al instante y cada cambio
 * se escribe en Firestore. Firestore tiene su propia caché en el celular, así que sin
 * Internet se sigue viendo y registrando, y se sube cuando vuelve la conexión.
 *
 * <p>Lo que llega de Firestore (otro celular, o la carga al entrar) se pasa a la memoria y
 * se avisa por {@link #cambios()} solo si de verdad cambió algo: así lo que escribe este
 * mismo celular no repinta dos veces la pantalla (ni le quita el foco a un campo).
 *
 * <p>Lo que se calcula no se guarda (RN-12).
 */
public final class Repositorio {

    private static final String ETIQUETA = "Repositorio";
    /** Donde se guardaba todo antes de Firebase. Se borra: la nube arranca vacía. */
    private static final String ARCHIVO_VIEJO = "datos.json";
    /** Semanas de un semestre mientras no se pongan las fechas (doc 02 · 1). */
    private static final int SEMANAS_POR_DEFECTO = 16;
    /** Forma de calificar predeterminada: tres cortes de 33, 33 y 34 % (doc 02 · 1). */
    private static final double[] CORTES_POR_DEFECTO = {33, 33, 34};
    /** El semestre en curso tiene siempre este id: así dos celulares no crean dos. */
    private static final String SEMESTRE_ACTUAL = "actual";
    private static final String EN_CURSO = "en_curso";
    private static final String TERMINADO = "terminado";

    /** Ids en orden de creación: los más cortos primero y, si miden igual, por texto. */
    private static final Comparator<String> POR_ID = (a, b) ->
            a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);

    private static Repositorio instancia;

    private final MutableLiveData<Integer> cambios = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> hayMomentos = new MutableLiveData<>(0);
    /** Cortes y semestres cerrados que todavía no se han celebrado en pantalla. */
    private final ArrayDeque<Momento> momentos = new ArrayDeque<>();
    /** De la aplicación, para los avisos (RF-31 y RF-32). */
    private final Context contexto;

    /** De quién son los datos que hay en memoria; {@code null} si no hay sesión. */
    @Nullable
    private String uid;
    private final List<ListenerRegistration> escuchas = new ArrayList<>();
    private final Map<String, ListenerRegistration> escuchasDeNotas = new HashMap<>();
    private final Map<String, ListenerRegistration> escuchasDeAnteriores = new HashMap<>();

    private String nombre;
    private String correo;
    private String universidad;
    /** Foto de Google (si entró con Google o la conectó en Perfil). */
    @Nullable
    private String fotoUrl;
    /** Lo escogido en Perfil: "foto", uno de los 8 avatares o "iniciales"; null = sin escoger. */
    @Nullable
    private String avatar;
    private boolean recordarNotas;
    private boolean avisarRiesgo;
    private LocalDate inicioSemestre;
    private LocalDate finSemestre;
    private List<Corte> formaDeCalificar;
    private double notaParaPasar;
    private final List<Materia> materias = new ArrayList<>();
    private final Map<String, List<Nota>> notas = new HashMap<>();
    private final List<Semestre> anteriores = new ArrayList<>();
    private long ultimoId;
    /** Materias cuyas notas ya llegaron: antes de eso no se sabe si están en rojo. */
    private final Set<String> notasCargadas = new HashSet<>();
    /** Sin el perfil no se sabe si el aviso de rojo está encendido. */
    private boolean perfilCargado;
    /** Cómo quedó programado el recordatorio en este celular; {@code null} = sin revisar. */
    @Nullable
    private Boolean recordatorioEncendido;

    /** Se llama una vez, al arrancar la app. Si ya había sesión, empieza a cargar. */
    public static void iniciar(Context contexto) {
        if (instancia != null) {
            return;
        }
        instancia = new Repositorio(contexto.getApplicationContext());
        borrarArchivoViejo(contexto);
        FirebaseUser yo = FirebaseAuth.getInstance().getCurrentUser();
        if (yo != null && Cuenta.haySesion()) {
            instancia.abrir(yo.getUid());
        }
    }

    public static Repositorio get() {
        if (instancia == null) {
            throw new IllegalStateException("Falta Repositorio.iniciar() en App");
        }
        return instancia;
    }

    private Repositorio(Context contexto) {
        this.contexto = contexto;
        limpiar();
    }

    /** Cambia cada vez que algo cambia. Las pantallas lo observan para repintarse. */
    public LiveData<Integer> cambios() {
        return cambios;
    }

    // ===== Sesión =====

    /** Empieza a escuchar los datos de esa cuenta. Lo llama {@link Cuenta} al entrar. */
    void abrir(String suUid) {
        if (suUid.equals(uid)) {
            return;
        }
        cerrar();
        uid = suUid;

        escuchas.add(refYo().addSnapshotListener((doc, error) -> {
            if (doc != null) {
                alCambiarPerfil(doc);
            } else {
                registrar("el perfil", error);
            }
        }));
        // Con los metadatos se sabe si "no existe" lo dijo el servidor o solo la caché
        escuchas.add(refSemestre(SEMESTRE_ACTUAL).addSnapshotListener(MetadataChanges.INCLUDE,
                (doc, error) -> {
                    if (doc != null) {
                        alCambiarSemestreActual(doc);
                    } else {
                        registrar("el semestre", error);
                    }
                }));
        escuchas.add(refMaterias(SEMESTRE_ACTUAL).addSnapshotListener((consulta, error) -> {
            if (consulta != null) {
                alCambiarMaterias(consulta);
            } else {
                registrar("las materias", error);
            }
        }));
        escuchas.add(refYo().collection("semestres").whereEqualTo("estado", TERMINADO)
                .addSnapshotListener((consulta, error) -> {
                    if (consulta != null) {
                        alCambiarAnteriores(consulta);
                    } else {
                        registrar("los semestres", error);
                    }
                }));
    }

    /** Deja de escuchar y olvida todo lo de la cuenta. Lo llama {@link Cuenta} al salir. */
    void cerrar() {
        for (ListenerRegistration escucha : escuchas) {
            escucha.remove();
        }
        escuchas.clear();
        quitarTodas(escuchasDeNotas);
        quitarTodas(escuchasDeAnteriores);
        if (uid != null) {
            // El recordatorio es de la cuenta que sale
            Avisos.apagarRecordatorio(contexto);
        }
        uid = null;
        limpiar();
        avisar();
    }

    private void limpiar() {
        nombre = "";
        correo = "";
        universidad = "";
        fotoUrl = null;
        avatar = null;
        recordarNotas = false;
        avisarRiesgo = false;
        inicioSemestre = LocalDate.now().minusWeeks(6);
        finSemestre = inicioSemestre.plusWeeks(SEMANAS_POR_DEFECTO);
        formaDeCalificar = formaPorDefecto(CORTES_POR_DEFECTO);
        notaParaPasar = 3.0;
        materias.clear();
        notas.clear();
        anteriores.clear();
        notasCargadas.clear();
        momentos.clear();
        perfilCargado = false;
        recordatorioEncendido = null;
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

    @Nullable
    public String fotoUrl() {
        return fotoUrl;
    }

    @Nullable
    public String avatar() {
        return avatar;
    }

    /** Perfil → Elige tu foto: "foto", uno de los 8 avatares o "iniciales". */
    public void guardarAvatar(String cual) {
        avatar = cual;
        escribirPerfil(Collections.<String, Object>singletonMap("avatar", cual));
        avisar();
    }

    /** Conectó su cuenta de Google en Perfil: desde ahora se ve su foto. */
    public void guardarFotoDeGoogle(String url) {
        fotoUrl = url;
        avatar = "foto";
        Map<String, Object> cambio = new HashMap<>();
        cambio.put("fotoUrl", url);
        cambio.put("avatar", avatar);
        escribirPerfil(cambio);
        avisar();
    }

    /**
     * Nombre y correo para saludar mientras llega el perfil (por ejemplo, entre crear la
     * cuenta y confirmar el correo). Ya están guardados en {@code usuarios/{uid}}.
     */
    public void guardarCuenta(String suNombre, String suCorreo) {
        nombre = suNombre.trim();
        correo = suCorreo.trim();
        avisar();
    }

    /**
     * ¿Dónde estudias? → Listo: su forma de calificar pasa a ser la plantilla (RF-03).
     * Se guarda el código SNIES y también el nombre, para mostrarlo sin buscarlo.
     */
    public void elegirUniversidad(String id, String suNombre, double[] cortes, double paraPasar) {
        universidad = suNombre;
        formaDeCalificar = formaPorDefecto(cortes);
        notaParaPasar = paraPasar;
        Map<String, Object> cambio = new HashMap<>();
        cambio.put("universidadId", id);
        cambio.put("universidad", suNombre);
        cambio.put("plantilla", plantillaAMapa(formaDeCalificar, notaParaPasar));
        escribirPerfil(cambio);
        avisar();
    }

    /** ¿Dónde estudias? → Saltar por ahora: queda la forma de calificar que haya. */
    public void empezarSinUniversidad() {
        avisar();
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
        Map<String, Object> cambio = new HashMap<>();
        cambio.put("recordarNotas", recordar);
        cambio.put("alertaRiesgo", riesgo);
        escribirPerfil(cambio);
        programarRecordatorio(recordar);
    }

    /** Solo se toca WorkManager cuando cambia: el perfil llega muchas veces. */
    private void programarRecordatorio(boolean encendido) {
        if (uid == null || Boolean.valueOf(encendido).equals(recordatorioEncendido)) {
            return;
        }
        recordatorioEncendido = encendido;
        if (encendido) {
            Avisos.encenderRecordatorio(contexto, ExistingWorkPolicy.KEEP);
        } else {
            Avisos.apagarRecordatorio(contexto);
        }
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
        if (uid != null) {
            guardarEnLaNube(refSemestre(SEMESTRE_ACTUAL)
                    .set(semestreActualAMapa(), SetOptions.merge()), "las fechas");
        }
        avisar();
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
        escribirPerfil(Collections.<String, Object>singletonMap("plantilla",
                plantillaAMapa(formaDeCalificar, notaParaPasar)));
        avisar();
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
        materias.sort((a, b) -> POR_ID.compare(a.id, b.id));
        notas.put(materia.id, new ArrayList<>());
        notasCargadas.add(materia.id);

        if (uid != null) {
            Map<String, Object> datos = materiaAMapa(materia);
            datos.put("creadaEn", FieldValue.serverTimestamp());
            guardarEnLaNube(refMateria(SEMESTRE_ACTUAL, materia.id).set(datos), "la materia");
        }
        avisar();
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
        Map<String, Object> cambio = new HashMap<>();
        cambio.put("nombre", suNombre);
        cambio.put("profesor", profesor);
        cambio.put("creditos", creditos);
        actualizarMateria(id, cambio);
        avisar();
    }

    /** Cambia la forma de calificar de una sola materia, sin tocar la plantilla. */
    public void guardarFormaDeMateria(String id, List<Corte> cortes, double paraPasar) {
        Materia materia = materia(id);
        if (materia == null) {
            return;
        }
        materia.cortes = copiar(cortes);
        materia.notaParaPasar = paraPasar;
        Map<String, Object> cambio = new HashMap<>();
        cambio.put("cortes", cortesAMapas(materia.cortes));
        cambio.put("notaParaPasar", paraPasar);
        actualizarMateria(id, cambio);
        avisar();
    }

    /** Se borran también sus notas (RF-12): Firestore no las borra solo. */
    public void borrarMateria(String id) {
        List<Nota> susNotas = new ArrayList<>(notas(id));
        for (int i = materias.size() - 1; i >= 0; i--) {
            if (materias.get(i).id.equals(id)) {
                materias.remove(i);
            }
        }
        notas.remove(id);
        notasCargadas.remove(id);
        ListenerRegistration escucha = escuchasDeNotas.remove(id);
        if (escucha != null) {
            escucha.remove();
        }

        if (uid != null) {
            WriteBatch lote = db().batch();
            for (Nota nota : susNotas) {
                lote.delete(refNota(id, nota.id));
            }
            lote.delete(refMateria(SEMESTRE_ACTUAL, id));
            guardarEnLaNube(lote.commit(), "el borrado de la materia");
        }
        avisar();
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
        ponerNota(materiaId, nota);
        avisar();
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
        if (uid != null) {
            guardarEnLaNube(refNota(materiaId, cambiada.id).set(notaAMapa(cambiada)), "la nota");
        }
        avisar();
    }

    /** @return la nota borrada, para poder deshacer con {@link #restaurarNota}. */
    @Nullable
    public Nota borrarNota(String materiaId, String notaId) {
        List<Nota> lista = listaDe(materiaId);
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).id.equals(notaId)) {
                Nota borrada = lista.remove(i);
                if (uid != null) {
                    guardarEnLaNube(refNota(materiaId, notaId).delete(), "el borrado de la nota");
                }
                avisar();
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
        ponerNota(materiaId, nota);
        avisar();
    }

    /**
     * Cuánto vale una nota "Con porcentajes". Se guarda sin avisar: la pantalla del corte
     * ya se repinta sola mientras se escribe, y repintarla toda le quitaría el foco al campo.
     */
    public void cambiarPeso(String materiaId, String notaId, @Nullable Double peso) {
        Nota nota = nota(materiaId, notaId);
        if (nota != null) {
            nota.peso = peso;
            if (uid != null) {
                guardarEnLaNube(refNota(materiaId, notaId).update("peso", peso), "el porcentaje");
            }
            // No repinta, pero el peso sí puede meter la materia en rojo
            revisarRiesgo();
        }
    }

    /** "Todas valen igual" ↔ "Con porcentajes" de un componente (RF-17). */
    public void cambiarModo(String materiaId, int numeroCorte, String componenteId, boolean promedioIgual) {
        Materia materia = materia(materiaId);
        Componente componente = componente(materia, numeroCorte, componenteId);
        if (materia != null && componente != null) {
            componente.promedioIgual = promedioIgual;
            actualizarMateria(materiaId,
                    Collections.<String, Object>singletonMap("cortes", cortesAMapas(materia.cortes)));
            avisar();
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

    private void ponerNota(String materiaId, Nota nota) {
        List<Nota> lista = listaDe(materiaId);
        lista.add(nota);
        lista.sort((a, b) -> POR_ID.compare(a.id, b.id));
        if (uid != null) {
            guardarEnLaNube(refNota(materiaId, nota.id).set(notaAMapa(nota)), "la nota");
        }
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

    /** Crea el semestre si no tiene id; si lo tiene, lo reemplaza con sus materias. */
    public void guardarAnterior(Semestre semestre) {
        Semestre previo = semestre.id == null ? null : anterior(semestre.id);
        if (semestre.id == null) {
            semestre.id = nuevoId();
        }
        Set<String> quedan = new HashSet<>();
        for (Materia materia : semestre.materias) {
            if (materia.id == null) {
                materia.id = nuevoId();
            }
            quedan.add(materia.id);
        }

        if (previo == null) {
            anteriores.add(semestre);
        } else {
            anteriores.set(anteriores.indexOf(previo), semestre);
        }

        if (uid != null) {
            WriteBatch lote = db().batch();
            Map<String, Object> datos = new HashMap<>();
            datos.put("periodo", semestre.periodo);
            datos.put("estado", TERMINADO);
            lote.set(refSemestre(semestre.id), datos, SetOptions.merge());
            if (previo != null) {
                for (Materia vieja : previo.materias) {
                    if (vieja.id != null && !quedan.contains(vieja.id)) {
                        lote.delete(refMateria(semestre.id, vieja.id));
                    }
                }
            }
            for (Materia materia : semestre.materias) {
                Map<String, Object> suMateria = materiaAMapa(materia);
                suMateria.put("creadaEn", FieldValue.serverTimestamp());
                lote.set(refMateria(semestre.id, materia.id), suMateria);
            }
            guardarEnLaNube(lote.commit(), "el semestre");
        }
        avisar();
    }

    // ===== Lo que llega de Firestore =====

    private void alCambiarPerfil(DocumentSnapshot doc) {
        if (!doc.exists()) {
            return;
        }
        Object antes = firmaPerfil();
        nombre = texto(doc.getString("nombre"));
        correo = texto(doc.getString("correo"));
        universidad = texto(doc.getString("universidad"));
        fotoUrl = doc.getString("fotoUrl");
        avatar = doc.getString("avatar");
        recordarNotas = Boolean.TRUE.equals(doc.getBoolean("recordarNotas"));
        avisarRiesgo = Boolean.TRUE.equals(doc.getBoolean("alertaRiesgo"));
        Object plantilla = doc.get("plantilla");
        if (plantilla instanceof Map) {
            Map<?, ?> suPlantilla = (Map<?, ?>) plantilla;
            notaParaPasar = numero(suPlantilla.get("notaParaPasar"), 3.0);
            List<Corte> cortes = cortesDe(suPlantilla.get("cortes"));
            if (!cortes.isEmpty()) {
                formaDeCalificar = cortes;
            }
        }
        perfilCargado = true;
        // En un celular nuevo, o si lo cambió en otro, el recordatorio queda como diga la nube
        programarRecordatorio(recordarNotas);
        avisarSiCambio(antes, firmaPerfil());
        // Si las notas llegaron antes que el perfil, hasta ahora no se podían juzgar
        revisarRiesgo();
    }

    private void alCambiarSemestreActual(DocumentSnapshot doc) {
        if (!doc.exists()) {
            // Solo se crea si el servidor confirma que no está (la caché puede estar vacía)
            if (!doc.getMetadata().isFromCache() && uid != null) {
                guardarEnLaNube(refSemestre(SEMESTRE_ACTUAL).set(semestreActualAMapa()),
                        "el semestre en curso");
            }
            return;
        }
        Object antes = firmaFechas();
        LocalDate inicio = fecha(doc.getString("fechaInicio"));
        LocalDate fin = fecha(doc.getString("fechaFin"));
        if (inicio != null && fin != null) {
            inicioSemestre = inicio;
            finSemestre = fin;
        }
        avisarSiCambio(antes, firmaFechas());
    }

    private void alCambiarMaterias(QuerySnapshot consulta) {
        Object antes = firmaMaterias(materias);
        Set<String> vigentes = new HashSet<>();
        materias.clear();
        for (DocumentSnapshot doc : consulta.getDocuments()) {
            Materia materia = materiaDe(doc);
            materias.add(materia);
            vigentes.add(materia.id);
            if (!notas.containsKey(materia.id)) {
                notas.put(materia.id, new ArrayList<>());
            }
            if (!escuchasDeNotas.containsKey(materia.id)) {
                escucharNotas(materia.id);
            }
        }
        materias.sort((a, b) -> POR_ID.compare(a.id, b.id));

        // Las borradas (aquí o en otro celular) dejan de escucharse
        Iterator<Map.Entry<String, ListenerRegistration>> escucha = escuchasDeNotas.entrySet().iterator();
        while (escucha.hasNext()) {
            Map.Entry<String, ListenerRegistration> una = escucha.next();
            if (!vigentes.contains(una.getKey())) {
                una.getValue().remove();
                notas.remove(una.getKey());
                notasCargadas.remove(una.getKey());
                escucha.remove();
            }
        }
        avisarSiCambio(antes, firmaMaterias(materias));
    }

    private void escucharNotas(String materiaId) {
        escuchasDeNotas.put(materiaId, refNotas(materiaId).addSnapshotListener((consulta, error) -> {
            if (consulta == null) {
                registrar("las notas", error);
                return;
            }
            if (!escuchasDeNotas.containsKey(materiaId)) {
                return;
            }
            Object antes = firmaNotas(notas(materiaId));
            List<Nota> lista = new ArrayList<>();
            for (DocumentSnapshot doc : consulta.getDocuments()) {
                lista.add(notaDe(doc));
            }
            lista.sort((a, b) -> POR_ID.compare(a.id, b.id));
            notas.put(materiaId, lista);
            if (notasCargadas.add(materiaId)) {
                // Primera vez que llegan: ya se puede saber si está en rojo
                revisarRiesgo();
            }
            avisarSiCambio(antes, firmaNotas(lista));
        }));
    }

    private void alCambiarAnteriores(QuerySnapshot consulta) {
        Object antes = firmaAnteriores();
        Map<String, Semestre> previos = new HashMap<>();
        for (Semestre semestre : anteriores) {
            previos.put(semestre.id, semestre);
        }
        Set<String> vigentes = new HashSet<>();
        anteriores.clear();
        for (DocumentSnapshot doc : consulta.getDocuments()) {
            Semestre semestre = new Semestre();
            semestre.id = doc.getId();
            semestre.periodo = texto(doc.getString("periodo"));
            Semestre previo = previos.get(semestre.id);
            if (previo != null) {
                semestre.materias = previo.materias;
            }
            anteriores.add(semestre);
            vigentes.add(semestre.id);
            if (!escuchasDeAnteriores.containsKey(semestre.id)) {
                escucharMateriasDe(semestre.id);
            }
        }
        Iterator<Map.Entry<String, ListenerRegistration>> escucha =
                escuchasDeAnteriores.entrySet().iterator();
        while (escucha.hasNext()) {
            Map.Entry<String, ListenerRegistration> una = escucha.next();
            if (!vigentes.contains(una.getKey())) {
                una.getValue().remove();
                escucha.remove();
            }
        }
        avisarSiCambio(antes, firmaAnteriores());
    }

    private void escucharMateriasDe(String semestreId) {
        escuchasDeAnteriores.put(semestreId, refMaterias(semestreId).addSnapshotListener((consulta, error) -> {
            if (consulta == null) {
                registrar("un semestre anterior", error);
                return;
            }
            Semestre semestre = anterior(semestreId);
            if (semestre == null) {
                return;
            }
            Object antes = firmaAnteriores();
            List<Materia> suyas = new ArrayList<>();
            for (DocumentSnapshot doc : consulta.getDocuments()) {
                suyas.add(materiaDe(doc));
            }
            suyas.sort((a, b) -> POR_ID.compare(a.id, b.id));
            semestre.materias = suyas;
            avisarSiCambio(antes, firmaAnteriores());
        }));
    }

    // ===== Firmas: para saber si lo que llegó cambia algo =====

    private Object firmaPerfil() {
        return Arrays.asList(nombre, correo, universidad, fotoUrl, avatar, recordarNotas, avisarRiesgo,
                plantillaAMapa(formaDeCalificar, notaParaPasar));
    }

    private Object firmaFechas() {
        return Arrays.asList(inicioSemestre, finSemestre);
    }

    private static Object firmaMaterias(List<Materia> lista) {
        List<Object> firma = new ArrayList<>();
        for (Materia materia : lista) {
            Map<String, Object> datos = materiaAMapa(materia);
            datos.put("id", materia.id);
            firma.add(datos);
        }
        return firma;
    }

    private static Object firmaNotas(List<Nota> lista) {
        List<Object> firma = new ArrayList<>();
        for (Nota nota : lista) {
            Map<String, Object> datos = notaAMapa(nota);
            datos.put("id", nota.id);
            firma.add(datos);
        }
        return firma;
    }

    private Object firmaAnteriores() {
        List<Object> firma = new ArrayList<>();
        for (Semestre semestre : anteriores) {
            firma.add(Arrays.asList(semestre.id, semestre.periodo, firmaMaterias(semestre.materias)));
        }
        return firma;
    }

    private void avisarSiCambio(Object antes, Object despues) {
        if (!antes.equals(despues)) {
            avisar();
        }
    }

    private void avisar() {
        Integer actual = cambios.getValue();
        int siguiente = actual == null ? 1 : actual + 1;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cambios.setValue(siguiente);
        } else {
            cambios.postValue(siguiente);
        }
        revisarRiesgo();
    }

    /**
     * RF-32: ¿alguna materia acaba de entrar en rojo? Y de paso: ¿se cerró un corte o el
     * semestre? Eso se celebra con un momento (ver {@link #tomarMomento()}).
     */
    private void revisarRiesgo() {
        if (uid == null || !perfilCargado) {
            return;
        }
        VigiaDeRiesgo.revisar(contexto, uid, materias, notas, notasCargadas, avisarRiesgo);
        List<Momento> nuevos = VigiaDeMomentos.revisar(contexto, uid, periodoActual(),
                semanasTotales(), materias, notas, notasCargadas);
        if (!nuevos.isEmpty()) {
            momentos.addAll(nuevos);
            Integer cuantos = hayMomentos.getValue();
            hayMomentos.setValue(cuantos == null ? 1 : cuantos + 1);
        }
    }

    /** Cambia cuando hay algo que celebrar. La Activity lo observa. */
    public LiveData<Integer> hayMomentos() {
        return hayMomentos;
    }

    /** El siguiente momento por mostrar, o {@code null} si no hay. */
    @Nullable
    public Momento tomarMomento() {
        return momentos.poll();
    }

    // ===== Escribir en Firestore =====

    private void escribirPerfil(Map<String, Object> cambio) {
        if (uid != null) {
            guardarEnLaNube(refYo().set(cambio, SetOptions.merge()), "el perfil");
        }
    }

    private void actualizarMateria(String id, Map<String, Object> cambio) {
        if (uid != null) {
            guardarEnLaNube(refMateria(SEMESTRE_ACTUAL, id).update(cambio), "la materia");
        }
    }

    /**
     * No se espera a que termine: sin Internet la tarea queda pendiente hasta que vuelva la
     * conexión, pero lo escrito ya está en la caché y las pantallas lo ven.
     */
    private static void guardarEnLaNube(Task<Void> escritura, String que) {
        escritura.addOnFailureListener(error -> Log.e(ETIQUETA, "No se pudo guardar " + que, error));
    }

    private static void registrar(String que, @Nullable Exception error) {
        Log.e(ETIQUETA, "No se pudo leer " + que, error);
    }

    /**
     * Ids en número (la navegación los pasa como long) y en orden de creación: la hora en
     * milisegundos, y uno más si se piden dos en el mismo milisegundo.
     */
    private String nuevoId() {
        ultimoId = Math.max(System.currentTimeMillis(), ultimoId + 1);
        return String.valueOf(ultimoId);
    }

    private static void quitarTodas(Map<String, ListenerRegistration> escuchas) {
        for (ListenerRegistration escucha : escuchas.values()) {
            escucha.remove();
        }
        escuchas.clear();
    }

    private static void borrarArchivoViejo(Context contexto) {
        File viejo = new File(contexto.getFilesDir(), ARCHIVO_VIEJO);
        File temporal = new File(contexto.getFilesDir(), ARCHIVO_VIEJO + ".tmp");
        if ((viejo.exists() && !viejo.delete()) || (temporal.exists() && !temporal.delete())) {
            Log.w(ETIQUETA, "No se pudo borrar " + ARCHIVO_VIEJO);
        }
    }

    // ===== Rutas (doc 05 · 2) =====

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private DocumentReference refYo() {
        if (uid == null) {
            throw new IllegalStateException("Sin sesión");
        }
        return db().collection("usuarios").document(uid);
    }

    private DocumentReference refSemestre(String semestreId) {
        return refYo().collection("semestres").document(semestreId);
    }

    private CollectionReference refMaterias(String semestreId) {
        return refSemestre(semestreId).collection("materias");
    }

    private DocumentReference refMateria(String semestreId, String materiaId) {
        return refMaterias(semestreId).document(materiaId);
    }

    private CollectionReference refNotas(String materiaId) {
        return refMateria(SEMESTRE_ACTUAL, materiaId).collection("notas");
    }

    private DocumentReference refNota(String materiaId, String notaId) {
        return refNotas(materiaId).document(notaId);
    }

    // ===== De objetos a Firestore y de vuelta (doc 05 · 3) =====

    private Map<String, Object> semestreActualAMapa() {
        Map<String, Object> datos = new HashMap<>();
        datos.put("periodo", periodoActual());
        datos.put("fechaInicio", inicioSemestre.toString());
        datos.put("fechaFin", finSemestre.toString());
        datos.put("estado", EN_CURSO);
        return datos;
    }

    private static Map<String, Object> plantillaAMapa(List<Corte> cortes, double paraPasar) {
        Map<String, Object> plantilla = new HashMap<>();
        plantilla.put("notaParaPasar", paraPasar);
        plantilla.put("cortes", cortesAMapas(cortes));
        return plantilla;
    }

    private static Map<String, Object> materiaAMapa(Materia materia) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", materia.nombre);
        datos.put("profesor", materia.profesor);
        datos.put("creditos", materia.creditos);
        datos.put("notaParaPasar", materia.notaParaPasar);
        datos.put("notaFinal", materia.notaFinal);
        datos.put("cortes", cortesAMapas(materia.cortes));
        return datos;
    }

    private static Materia materiaDe(DocumentSnapshot doc) {
        Materia materia = new Materia();
        materia.id = doc.getId();
        materia.nombre = texto(doc.getString("nombre"));
        materia.profesor = doc.getString("profesor");
        materia.creditos = (int) numero(doc.get("creditos"), 0);
        materia.notaParaPasar = numero(doc.get("notaParaPasar"), 3.0);
        materia.notaFinal = numeroONulo(doc.get("notaFinal"));
        materia.cortes = cortesDe(doc.get("cortes"));
        return materia;
    }

    private static List<Map<String, Object>> cortesAMapas(List<Corte> cortes) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Corte corte : cortes) {
            List<Map<String, Object>> componentes = new ArrayList<>();
            for (Componente componente : corte.componentes) {
                Map<String, Object> c = new HashMap<>();
                c.put("id", componente.id);
                c.put("nombre", componente.nombre);
                c.put("porcentaje", componente.porcentaje);
                c.put("promedioIgual", componente.promedioIgual);
                componentes.add(c);
            }
            Map<String, Object> datos = new HashMap<>();
            datos.put("numero", corte.numero);
            datos.put("porcentaje", corte.porcentaje);
            datos.put("componentes", componentes);
            lista.add(datos);
        }
        return lista;
    }

    private static List<Corte> cortesDe(@Nullable Object valor) {
        List<Corte> cortes = new ArrayList<>();
        if (!(valor instanceof List)) {
            return cortes;
        }
        for (Object uno : (List<?>) valor) {
            if (!(uno instanceof Map)) {
                continue;
            }
            Map<?, ?> corte = (Map<?, ?>) uno;
            List<Componente> componentes = new ArrayList<>();
            Object susComponentes = corte.get("componentes");
            if (susComponentes instanceof List) {
                for (Object otro : (List<?>) susComponentes) {
                    if (otro instanceof Map) {
                        Map<?, ?> c = (Map<?, ?>) otro;
                        componentes.add(new Componente(texto(c.get("id")), texto(c.get("nombre")),
                                numero(c.get("porcentaje"), 0),
                                !Boolean.FALSE.equals(c.get("promedioIgual"))));
                    }
                }
            }
            cortes.add(new Corte((int) numero(corte.get("numero"), 0),
                    numero(corte.get("porcentaje"), 0), componentes));
        }
        return cortes;
    }

    private static Map<String, Object> notaAMapa(Nota nota) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("corte", nota.corte);
        datos.put("componenteId", nota.componenteId);
        datos.put("nombre", nota.nombre);
        datos.put("valor", nota.valor);
        datos.put("peso", nota.peso);
        datos.put("fecha", nota.fecha);
        return datos;
    }

    private static Nota notaDe(DocumentSnapshot doc) {
        Nota nota = new Nota((int) numero(doc.get("corte"), 1), texto(doc.getString("componenteId")),
                numero(doc.get("valor"), 0), numeroONulo(doc.get("peso")));
        nota.id = doc.getId();
        nota.nombre = doc.getString("nombre");
        nota.fecha = doc.getString("fecha");
        return nota;
    }

    private static double numero(@Nullable Object valor, double siFalta) {
        return valor instanceof Number ? ((Number) valor).doubleValue() : siFalta;
    }

    @Nullable
    private static Double numeroONulo(@Nullable Object valor) {
        return valor instanceof Number ? ((Number) valor).doubleValue() : null;
    }

    private static String texto(@Nullable Object valor) {
        return valor instanceof String ? (String) valor : "";
    }

    @Nullable
    private static LocalDate fecha(@Nullable String valor) {
        try {
            return valor == null ? null : LocalDate.parse(valor);
        } catch (RuntimeException error) {
            return null;
        }
    }
}
