package com.dosnueve.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.data.Cuenta;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.domain.Momento;
import com.dosnueve.app.ui.acciones.AccionesBottomSheet;
import com.dosnueve.app.ui.momento.MomentoDialog;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Una sola Activity con NavHostFragment (doc 06 · 2).
 *
 * <p>La barra de navegación inferior es un LinearLayout propio con el botón + como
 * círculo superpuesto (doc 04 · 4). Aquí se decide en qué pantallas se muestra y cuál
 * pestaña queda marcada.
 */
public class MainActivity extends AppCompatActivity {

    /** Pantallas que llevan barra inferior: 03, 10, 11, 12, 18, 19 y 20 (doc 03). */
    private static final Set<Integer> PANTALLAS_CON_BARRA = new HashSet<>(Arrays.asList(
            R.id.inicioFragment,
            R.id.semestresFragment,
            R.id.simularFragment,
            R.id.perfilFragment
    ));

    /** Cada pestaña con el destino al que lleva. */
    private static final List<int[]> PESTANAS = Arrays.asList(
            new int[]{R.id.pestanaInicio, R.id.inicioFragment},
            new int[]{R.id.pestanaSemestres, R.id.semestresFragment},
            new int[]{R.id.pestanaSimular, R.id.simularFragment},
            new int[]{R.id.pestanaPerfil, R.id.perfilFragment});

    private static final int DURACION_CON_ACCION = 6000;

    /** Qué abrir al tocar una notificación (ver {@link com.dosnueve.app.avisos.Avisos}). */
    public static final String EXTRA_ABRIR = "abrir";
    public static final String ABRIR_REGISTRAR = "registrar";
    public static final String ABRIR_MATERIA = "materia";
    public static final String EXTRA_MATERIA = "materiaId";
    /** Si los datos no llegan en este tiempo, se queda en el Inicio. */
    private static final long ESPERA_MAXIMA = 5000;

    private NavController navegacion;
    private View barraNavegacion;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        // El fondo es color papel, así que los íconos del sistema van oscuros.
        // EdgeToEdge.enable(this) a secas los deja claros y no se leen.
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_main);

        View raiz = findViewById(R.id.raiz);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, insets) -> {
            Insets barrasDelSistema = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Con el teclado abierto, el contenido se acorta hasta su borde: así el botón de
            // guardar de Registrar nota o Nueva materia queda justo encima, no detrás
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
            vista.setPadding(barrasDelSistema.left, barrasDelSistema.top,
                    barrasDelSistema.right, Math.max(barrasDelSistema.bottom, teclado.bottom));
            return insets;
        });

        NavHostFragment anfitrion = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.contenedorNav);
        if (anfitrion == null) {
            throw new IllegalStateException("Falta el NavHostFragment en activity_main");
        }
        navegacion = anfitrion.getNavController();

        barraNavegacion = findViewById(R.id.barraNavegacion);
        conectarPestanas();

        navegacion.addOnDestinationChangedListener(
                (controlador, destino, argumentos) -> alCambiarDePantalla(destino));

        // Con la sesión abierta se entra directo al Inicio, sin pasar por la Bienvenida
        if (estado == null && Cuenta.haySesion()) {
            navegacion.navigate(R.id.inicioFragment, null, new NavOptions.Builder()
                    .setPopUpTo(R.id.bienvenidaFragment, true)
                    .build());
            abrirDesdeAviso(getIntent());
        }

        // Se cerró un corte o el semestre: a celebrarlo
        Repositorio.get().hayMomentos().observe(this, cuantos -> mostrarMomentoLuego());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Por si quedó alguno en fila mientras la app estaba atrás
        mostrarMomentoLuego();
    }

    private final Runnable mostrarMomento = this::mostrarSiguienteMomento;

    /**
     * Un momento después: deja que se cierre la pantalla de la nota y salga el aviso de
     * "Nota guardada" antes de la celebración.
     */
    public void mostrarMomentoLuego() {
        barraNavegacion.removeCallbacks(mostrarMomento);
        barraNavegacion.postDelayed(mostrarMomento, 700);
    }

    private void mostrarSiguienteMomento() {
        FragmentManager fragmentos = getSupportFragmentManager();
        if (isFinishing() || fragmentos.isStateSaved()
                || fragmentos.findFragmentByTag(MomentoDialog.ETIQUETA) != null) {
            return;
        }
        Momento momento = Repositorio.get().tomarMomento();
        if (momento != null) {
            MomentoDialog.de(momento).show(fragmentos, MomentoDialog.ETIQUETA);
        }
    }

    /**
     * Se tocó una notificación: Registrar nota (recordatorio) o la materia que entró en
     * rojo. La app arrancó de cero, así que se espera a que lleguen las materias de
     * Firestore: si se navega antes, la pantalla no encuentra la materia y se cierra.
     */
    private void abrirDesdeAviso(Intent intento) {
        String que = intento.getStringExtra(EXTRA_ABRIR);
        if (que == null) {
            return;
        }
        long materiaId = intento.getLongExtra(EXTRA_MATERIA, 0);
        Repositorio datos = Repositorio.get();

        Observer<Integer> cuandoLleguen = new Observer<Integer>() {
            @Override
            public void onChanged(Integer cambio) {
                boolean listos = ABRIR_MATERIA.equals(que)
                        ? datos.materia(String.valueOf(materiaId)) != null
                        : !datos.materias().isEmpty();
                if (!listos) {
                    return;
                }
                datos.cambios().removeObserver(this);
                if (ABRIR_MATERIA.equals(que)) {
                    Bundle argumentos = new Bundle();
                    argumentos.putLong(EXTRA_MATERIA, materiaId);
                    navegacion.navigate(R.id.detalleMateriaFragment, argumentos);
                } else {
                    navegacion.navigate(R.id.registrarNotaFragment);
                }
            }
        };
        datos.cambios().observe(this, cuandoLleguen);
        // Sin materias (o sin Internet y sin caché) no se abre nada más tarde de sorpresa
        barraNavegacion.postDelayed(() -> datos.cambios().removeObserver(cuandoLleguen), ESPERA_MAXIMA);
    }

    /**
     * Aviso de abajo con una acción, como "Nota guardada en Cálculo II · Corte 3 ·
     * Deshacer" (pantalla 19). Va en la Activity y no en el Fragment porque se muestra
     * justo después de cerrar la pantalla que lo pide. Si la barra inferior está a la
     * vista, el aviso sale encima de ella.
     */
    public void avisar(String texto, @Nullable String accion, @Nullable Runnable alTocar) {
        Snackbar aviso = Snackbar.make(findViewById(R.id.raiz), texto, Snackbar.LENGTH_LONG);
        if (accion != null && alTocar != null) {
            aviso.setAction(accion, vista -> alTocar.run());
            // Con "Deshacer" se deja más tiempo: los 2.7 s de LENGTH_LONG no alcanzan para leer y tocar
            aviso.setDuration(DURACION_CON_ACCION);
        }
        if (barraNavegacion.getVisibility() == View.VISIBLE) {
            aviso.setAnchorView(barraNavegacion);
        }
        aviso.show();
    }

    private void conectarPestanas() {
        for (int[] pestana : PESTANAS) {
            irAlTocar(pestana[0], pestana[1]);
        }

        // El botón + abre la hoja 04 Acciones rápidas
        barraNavegacion.findViewById(R.id.botonMas).setOnClickListener(vista ->
                new AccionesBottomSheet().show(getSupportFragmentManager(),
                        AccionesBottomSheet.ETIQUETA));
    }

    private void irAlTocar(int idPestana, int idDestino) {
        barraNavegacion.findViewById(idPestana).setOnClickListener(vista -> {
            NavDestination actual = navegacion.getCurrentDestination();
            if (actual != null && actual.getId() == idDestino) {
                return;
            }
            // Inicio siempre está abajo en el historial: se vuelve a él. Si se navegara con
            // restoreState, la librería le asigna la primera pestaña que se guardó y
            // tocar Inicio terminaba abriendo Perfil o Semestres
            if (idDestino == R.id.inicioFragment
                    && navegacion.popBackStack(R.id.inicioFragment, false)) {
                return;
            }
            navegacion.navigate(idDestino, null, opcionesDePestana());
        });
    }

    /**
     * Cambiar de pestaña no apila pantallas.
     *
     * <p>Sin esto, cada toque agrega un destino al historial y el botón atrás termina
     * recorriendo el paseo del usuario por las pestañas en vez de salir de la app.
     *
     * <p>Se vuelve al Inicio, no al destino inicial del grafo: ese es Bienvenida, y
     * atrás nunca debe devolver al login. saveState y restoreState conservan el
     * scroll de cada pestaña.
     */
    private NavOptions opcionesDePestana() {
        return new NavOptions.Builder()
                .setPopUpTo(R.id.inicioFragment, false, true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build();
    }

    private void alCambiarDePantalla(@NonNull NavDestination destino) {
        cerrarTeclado();

        boolean llevaBarra = PANTALLAS_CON_BARRA.contains(destino.getId());
        barraNavegacion.setVisibility(llevaBarra ? View.VISIBLE : View.GONE);

        if (llevaBarra) {
            for (int[] pestana : PESTANAS) {
                marcar(barraNavegacion.findViewById(pestana[0]), pestana[1] == destino.getId());
            }
        }
    }

    /**
     * Marca una pestaña como activa. El estado se propaga a los hijos para que el
     * ícono y el texto tomen el azul oscuro de {@code texto_pestana}.
     */
    private void marcar(View vista, boolean activa) {
        vista.setSelected(activa);
        if (vista instanceof ViewGroup) {
            ViewGroup grupo = (ViewGroup) vista;
            for (int i = 0; i < grupo.getChildCount(); i++) {
                marcar(grupo.getChildAt(i), activa);
            }
        }
    }

    /**
     * Al llegar a una pantalla nueva, ningún campo de texto debe quedarse con el foco:
     * si no, el teclado se abre solo y tapa media pantalla.
     */
    private void cerrarTeclado() {
        View conFoco = getCurrentFocus();
        if (conFoco == null) {
            return;
        }
        conFoco.clearFocus();
        new WindowInsetsControllerCompat(getWindow(), conFoco)
                .hide(WindowInsetsCompat.Type.ime());
    }

    /** La usan los Fragment para anclar el Snackbar encima de la barra (pantalla 19). */
    public View getBarraNavegacion() {
        return barraNavegacion;
    }
}
