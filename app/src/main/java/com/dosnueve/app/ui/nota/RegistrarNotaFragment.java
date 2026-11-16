package com.dosnueve.app.ui.nota;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.MainActivity;
import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.dosnueve.app.data.modelo.Componente;
import com.dosnueve.app.data.modelo.Corte;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.data.modelo.Nota;
import com.dosnueve.app.domain.CalculadoraNotas;
import com.dosnueve.app.ui.LimiteNota;
import com.dosnueve.app.ui.TextoPendientes;
import com.dosnueve.app.util.Formato;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 09 Registrar nota (doc 03, RF-13 a RF-15).
 *
 * <p>Materia, corte y tipo salen del {@link Repositorio}; el corte en curso viene marcado.
 * La vista previa calcula con {@link CalculadoraNotas} sobre las notas reales más la nueva.
 *
 * <p>La nota se escribe sin punto: entre 0.0 y 5.0 con un decimal solo hay 51
 * valores posibles, así que el campo lo pone solo (se teclea 45 y queda 4.5). El
 * punto del teclado numérico de Android está escondido y era el toque más molesto
 * del camino de cuatro toques (RNF-03).
 */
public class RegistrarNotaFragment extends Fragment {

    private static final String ARG_MATERIA = "materiaId";
    private static final double NOTA_MAXIMA = 5.0;
    private static final Locale ESPANOL = new Locale("es", "CO");

    private TextInputLayout campoNota;
    private TextInputEditText valorNota;
    private MaterialButton botonGuardar;
    private TextView textoVistaPrevia;
    private LinearLayout chipsTipo;
    private ChipGroup chipsCorte;
    /** Nombre y "Vale %": solo en los tipos de varias notas. */
    private View filaTrabajos;
    private TextView etiquetaPeso;
    private TextInputEditText valorPeso;
    private TextView pesoDisponible;
    private TextInputEditText nombreNota;

    private List<Materia> materias = Collections.emptyList();
    private Materia materia;
    private Corte corte;
    /** Lo escrito en cada tipo de nota (nombre, nota, peso), para no mezclarlos. */
    private String[][] borradores = new String[0][];
    private int tipoElegido;
    private boolean formateando;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_registrar_nota, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        campoNota = vista.findViewById(R.id.campoNota);
        valorNota = vista.findViewById(R.id.valorNota);
        botonGuardar = vista.findViewById(R.id.botonGuardar);
        textoVistaPrevia = vista.findViewById(R.id.textoVistaPrevia);
        chipsTipo = vista.findViewById(R.id.chipsTipo);
        chipsCorte = vista.findViewById(R.id.chipsCorte);
        filaTrabajos = vista.findViewById(R.id.filaTrabajos);
        etiquetaPeso = vista.findViewById(R.id.etiquetaPeso);
        valorPeso = vista.findViewById(R.id.valorPeso);
        pesoDisponible = vista.findViewById(R.id.pesoDisponible);
        nombreNota = vista.findViewById(R.id.nombre);

        vista.findViewById(R.id.botonCerrar).setOnClickListener(v -> volver());

        materias = new ArrayList<>(Repositorio.get().materias());
        if (materias.isEmpty()) {
            // Sin materias no hay dónde registrar: se va a crear la primera
            Toast.makeText(requireContext(), R.string.nota_sin_materias, Toast.LENGTH_SHORT).show();
            vista.post(() -> NavHostFragment.findNavController(this).navigate(R.id.nuevaMateriaFragment,
                    null, new NavOptions.Builder().setPopUpTo(R.id.registrarNotaFragment, true).build()));
            return;
        }

        valorNota.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                if (!formateando) {
                    ponerElPuntoSolo(texto);
                }
                alCambiarLaNota();
            }
        });
        LimiteNota.vigilar(valorNota);
        valorPeso.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                alCambiarLaNota();
            }
        });
        botonGuardar.setOnClickListener(v -> guardar());

        long pedida = getArguments() == null ? 0 : getArguments().getLong(ARG_MATERIA, 0);
        int cual = 0;
        for (int i = 0; i < materias.size(); i++) {
            if (materias.get(i).id.equals(String.valueOf(pedida))) {
                cual = i;
            }
        }
        llenarMaterias(vista.findViewById(R.id.chipsMateria), cual);
        escogerMateria(cual);

        // Cuando el teclado acorta la pantalla, se baja hasta ver la tarjeta de la nota
        // completa: el efecto va debajo del número y si no, quedaría detrás de Guardar
        NestedScrollView desplazable = vista.findViewById(R.id.desplazable);
        View tarjetaNota = vista.findViewById(R.id.tarjetaNota);
        desplazable.addOnLayoutChangeListener((v, izq, arriba, der, abajo, izqAntes, arribaAntes,
                                               derAntes, abajoAntes) -> {
            if (abajo - arriba != abajoAntes - arribaAntes && valorNota.hasFocus()) {
                desplazable.post(() -> {
                    int finTarjeta = desplazable.getPaddingTop() + tarjetaNota.getBottom()
                            + desplazable.getPaddingBottom();
                    int destino = finTarjeta - desplazable.getHeight();
                    if (destino > desplazable.getScrollY()) {
                        desplazable.smoothScrollTo(0, destino);
                    }
                });
            }
        });

        // La nota es lo único que hay que escribir: con el teclado ya abierto sobra un toque
        valorNota.post(() -> {
            if (!isAdded()) {
                return;
            }
            valorNota.requestFocus();
            WindowCompat.getInsetsController(requireActivity().getWindow(), valorNota)
                    .show(WindowInsetsCompat.Type.ime());
        });
    }

    // ===== Selectores =====

    private void llenarMaterias(ChipGroup grupo, int elegida) {
        grupo.removeAllViews();
        for (int i = 0; i < materias.size(); i++) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(materias.get(i).nombre);
            chip.setCheckable(true);
            chip.setChecked(i == elegida);
            final int cual = i;
            chip.setOnCheckedChangeListener((boton, marcado) -> {
                if (marcado) {
                    escogerMateria(cual);
                }
            });
            grupo.addView(chip);
        }
    }

    private void escogerMateria(int cual) {
        materia = materias.get(cual);
        llenarCortes();
    }

    /**
     * El corte en curso viene marcado y se nombra "actual" para que se note (RF-13); los
     * que ya están completos llevan ✓.
     */
    private void llenarCortes() {
        chipsCorte.removeAllViews();
        List<Nota> notas = notasDeLaMateria();
        Corte enCurso = CalculadoraNotas.corteEnCurso(materia, notas);
        if (enCurso == null && !materia.cortes.isEmpty()) {
            enCurso = materia.cortes.get(materia.cortes.size() - 1);
        }
        for (Corte unCorte : materia.cortes) {
            String nombre = getString(R.string.forma_corte_numero, unCorte.numero);
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            boolean esElActual = unCorte == enCurso;
            boolean completo = CalculadoraNotas.corteEvaluado(unCorte, notas);
            chip.setText(completo ? getString(R.string.nota_corte_listo, nombre)
                    : esElActual ? getString(R.string.nota_corte_actual, nombre)
                    : nombre);
            chip.setCheckable(true);
            chip.setChecked(esElActual);
            chip.setOnCheckedChangeListener((boton, marcado) -> {
                if (marcado) {
                    escogerCorte(unCorte);
                }
            });
            chipsCorte.addView(chip);
        }
        if (enCurso != null) {
            escogerCorte(enCurso);
        }
    }

    private void escogerCorte(Corte elegido) {
        corte = elegido;
        borradores = new String[corte.componentes.size()][];
        tipoElegido = tipoPorDefecto();
        cargarBorrador(tipoElegido);
        llenarTipos();
        alCambiarLaNota();
    }

    /** Trabajos y quices si el corte lo tiene: es lo que más se registra. */
    private int tipoPorDefecto() {
        for (int i = 0; i < corte.componentes.size(); i++) {
            if (CalculadoraNotas.ID_TRABAJOS.equals(corte.componentes.get(i).id)) {
                return i;
            }
        }
        return 0;
    }

    /** Chips de dos líneas: el nombre y cuánto pesa dentro del corte. */
    private void llenarTipos() {
        chipsTipo.removeAllViews();
        for (int i = 0; i < corte.componentes.size(); i++) {
            Componente componente = corte.componentes.get(i);
            View chip = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_chip_tipo, chipsTipo, false);

            TextView nombre = chip.findViewById(R.id.nombre);
            TextView peso = chip.findViewById(R.id.porcentaje);
            nombre.setText(nombreCorto(componente));
            // Si el parcial o la autoevaluación ya tienen nota, se muestra: la nueva la reemplaza
            Nota yaTiene = notaUnicaDe(componente);
            peso.setText(yaTiene != null
                    ? getString(R.string.nota_tipo_tiene, Formato.nota(yaTiene.valor))
                    : getString(R.string.general_porcentaje, Formato.porcentaje(componente.porcentaje)));

            if (i > 0) {
                LinearLayout.LayoutParams medidas =
                        (LinearLayout.LayoutParams) chip.getLayoutParams();
                medidas.setMarginStart((int) getResources().getDimension(R.dimen.espacio_s));
                chip.setLayoutParams(medidas);
            }

            final int cual = i;
            chip.setOnClickListener(v -> {
                if (cual == tipoElegido) {
                    return;
                }
                guardarBorrador(tipoElegido);
                tipoElegido = cual;
                cargarBorrador(cual);
                marcarTipos();
                alCambiarLaNota();
            });
            chipsTipo.addView(chip);
        }
        marcarTipos();
    }

    /** Los chips son angostos: "Trabajo o quiz" y "Autoeval." en vez del nombre largo. */
    private static String nombreCorto(Componente componente) {
        if (CalculadoraNotas.ID_TRABAJOS.equals(componente.id)) {
            return "Trabajo o quiz";
        }
        if (CalculadoraNotas.ID_AUTOEVALUACION.equals(componente.id)) {
            return "Autoeval.";
        }
        return componente.nombre;
    }

    /** Cómo se nombra cada tipo dentro de la vista previa. */
    private String frase(Componente componente) {
        if (CalculadoraNotas.ID_PARCIAL.equals(componente.id)) {
            return getString(R.string.hoja_el_parcial);
        }
        if (CalculadoraNotas.ID_TRABAJOS.equals(componente.id)) {
            return getString(R.string.hoja_trabajos);
        }
        if (CalculadoraNotas.ID_AUTOEVALUACION.equals(componente.id)) {
            return getString(R.string.hoja_la_autoevaluacion);
        }
        return componente.nombre.toLowerCase(ESPANOL);
    }

    /**
     * Cada tipo de nota es una zona aparte: lo escrito en Trabajos no pasa a la
     * Autoevaluación, y al volver a Trabajos aparece otra vez.
     */
    private void guardarBorrador(int tipo) {
        borradores[tipo] = new String[]{texto(nombreNota), texto(valorNota), texto(valorPeso)};
    }

    private void cargarBorrador(int tipo) {
        String[] borrador = borradores[tipo];
        nombreNota.setText(borrador == null ? null : borrador[0]);
        valorNota.setText(borrador == null ? null : borrador[1]);
        valorPeso.setText(borrador == null ? null : borrador[2]);
    }

    private static String texto(TextView campo) {
        return campo.getText() == null ? "" : campo.getText().toString();
    }

    private void marcarTipos() {
        for (int i = 0; i < chipsTipo.getChildCount(); i++) {
            View chip = chipsTipo.getChildAt(i);
            boolean elegido = i == tipoElegido;
            chip.setSelected(elegido);
            chip.findViewById(R.id.nombre).setSelected(elegido);
            chip.findViewById(R.id.porcentaje).setSelected(elegido);
        }
    }

    // ===== Datos =====

    private List<Nota> notasDeLaMateria() {
        return Repositorio.get().notas(materia.id);
    }

    private Componente componenteElegido() {
        return corte.componentes.get(tipoElegido);
    }

    /** El parcial y la autoevaluación son una sola nota: si ya la tiene, se reemplaza. */
    @Nullable
    private Nota notaQueSeReemplaza() {
        return notaUnicaDe(componenteElegido());
    }

    /** La nota que ya tiene un tipo de una sola nota en este corte; {@code null} si no. */
    @Nullable
    private Nota notaUnicaDe(Componente componente) {
        if (!CalculadoraNotas.esUnaSolaNota(componente.id)) {
            return null;
        }
        for (Nota nota : notasDeLaMateria()) {
            if (nota.corte == corte.numero && Objects.equals(nota.componenteId, componente.id)) {
                return nota;
            }
        }
        return null;
    }

    /** Las notas de la materia sin la que se va a reemplazar. */
    private List<Nota> notasSinLaReemplazada() {
        Nota reemplazada = notaQueSeReemplaza();
        List<Nota> notas = new ArrayList<>();
        for (Nota nota : notasDeLaMateria()) {
            if (nota != reemplazada) {
                notas.add(nota);
            }
        }
        return notas;
    }

    private double meta() {
        Double necesitas = CalculadoraNotas.resumen(materia, notasDeLaMateria()).necesitas;
        return necesitas == null ? materia.notaParaPasar : necesitas;
    }

    // ===== El campo de nota =====

    /**
     * Pone el punto decimal solo: se teclean dígitos y el campo los acomoda.
     * 4 → "4" · 45 → "4.5" · 50 → "5.0"
     */
    private void ponerElPuntoSolo(Editable texto) {
        String soloDigitos = texto.toString().replaceAll("[^0-9]", "");
        if (soloDigitos.length() > 2) {
            soloDigitos = soloDigitos.substring(0, 2);
        }

        String acomodado = soloDigitos.length() <= 1
                ? soloDigitos
                : soloDigitos.charAt(0) + "." + soloDigitos.charAt(1);

        if (acomodado.contentEquals(texto)) {
            return;
        }
        formateando = true;
        texto.replace(0, texto.length(), acomodado);
        // El largo real, no el de "acomodado": un filtro del campo puede recortarlo
        valorNota.setSelection(texto.length());
        formateando = false;
    }

    /**
     * Revisa nota y peso cada vez que cambia algo. El peso (RN-04) es obligatorio si el
     * componente va "Con porcentajes" y opcional si sus notas valen igual: escribirlo lo
     * pasa a "Con porcentajes". No puede pasar de lo que le queda al componente.
     */
    private void alCambiarLaNota() {
        if (corte == null || corte.componentes.isEmpty()) {
            botonGuardar.setEnabled(false);
            return;
        }
        Componente componente = componenteElegido();
        boolean llevaPeso = !CalculadoraNotas.esUnaSolaNota(componente.id);
        boolean pidePeso = llevaPeso && !componente.promedioIgual;
        filaTrabajos.setVisibility(llevaPeso ? View.VISIBLE : View.GONE);
        etiquetaPeso.setText(pidePeso ? R.string.nota_peso : R.string.nota_peso_opcional);

        Double nota = leerNota();
        Double peso = llevaPeso ? leerPeso() : null;
        double disponible = CalculadoraNotas.pesoDisponible(corte.numero, componente, notasDeLaMateria());
        boolean sePasa = peso != null && peso > disponible;
        mostrarDisponible(componente, peso, disponible, sePasa);

        boolean fueraDeRango = nota != null && (nota < 0 || nota > NOTA_MAXIMA);
        campoNota.setError(fueraDeRango ? getString(R.string.nota_error_rango) : null);

        boolean notaValida = nota != null && !fueraDeRango;
        boolean faltaPeso = pidePeso && peso == null;
        botonGuardar.setEnabled(notaValida && !faltaPeso && !sePasa);

        if (!notaValida || sePasa) {
            enReposo();
        } else if (faltaPeso) {
            pedirPeso(componente, disponible);
        } else {
            mostrarEfecto(nota, peso);
        }
    }

    /** "quedan 10 de 30 %" al lado del campo; en rojo si el peso escrito se pasa. */
    private void mostrarDisponible(Componente componente, Double peso, double disponible,
                                   boolean sePasa) {
        String quedan = Formato.porcentaje(Math.max(disponible, 0));
        String total = Formato.porcentaje(componente.porcentaje);
        if (sePasa) {
            pesoDisponible.setText(getString(R.string.nota_peso_se_pasa, quedan, total));
        } else if (componente.promedioIgual && peso == null) {
            pesoDisponible.setText(R.string.nota_peso_igual);
        } else {
            pesoDisponible.setText(getString(R.string.nota_peso_quedan, quedan, total));
        }
        pesoDisponible.setTextColor(ContextCompat.getColor(requireContext(),
                sePasa ? R.color.riesgo : R.color.texto_secundario));
    }

    /** El peso escrito, en % del corte. {@code null} si está vacío o en 0. */
    @Nullable
    private Double leerPeso() {
        CharSequence texto = valorPeso.getText();
        if (texto == null || texto.toString().trim().isEmpty()) {
            return null;
        }
        try {
            double peso = Double.parseDouble(texto.toString().trim());
            return peso > 0 ? peso : null;
        } catch (NumberFormatException sinNumero) {
            return null;
        }
    }

    @Nullable
    private Double leerNota() {
        CharSequence texto = valorNota.getText();
        if (texto == null || texto.toString().trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(texto.toString().trim());
        } catch (NumberFormatException sinNumero) {
            return null;
        }
    }

    // ===== Vista previa (RF-14) =====

    /** Sin nota todavía: se dice qué va a pasar, en gris. */
    private void enReposo() {
        textoVistaPrevia.setText(R.string.nota_previa_reposo);
        textoVistaPrevia.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.texto_secundario));
    }

    /** Nota escrita pero falta cuánto vale: se pregunta, en gris. */
    private void pedirPeso(Componente componente, double disponible) {
        textoVistaPrevia.setText(getString(R.string.nota_previa_falta_peso,
                Formato.porcentaje(Math.max(disponible, 0)),
                Formato.porcentaje(componente.porcentaje)));
        textoVistaPrevia.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.texto_secundario));
    }

    /**
     * Con nota: el mensaje a mano con el efecto real, calculado con CalculadoraNotas sobre
     * las notas de la materia más la nueva (con su peso, si lo tiene). Se calcula sobre una
     * copia del corte, para no tocar la forma de calificar guardada.
     */
    private void mostrarEfecto(double nota, @Nullable Double peso) {
        List<Corte> copia = new ArrayList<>();
        copia.add(corte);
        Corte corteCopia = Repositorio.copiar(copia).get(0);
        Componente componente = corteCopia.componentes.get(tipoElegido);
        List<Nota> notas = notasSinLaReemplazada();
        boolean unaSola = CalculadoraNotas.esUnaSolaNota(componente.id);

        Double antes = CalculadoraNotas.notaComponente(corte.numero, componente, notasDeLaMateria());
        if (peso != null) {
            // Con un peso escrito, el componente pasa a "Con porcentajes"
            componente.promedioIgual = false;
        }
        notas.add(new Nota(corte.numero, componente.id, nota, peso));
        double despues = CalculadoraNotas.notaComponente(corte.numero, componente, notas);
        CalculadoraNotas.NecesarioEnCorte falta =
                CalculadoraNotas.necesarioEnCorte(corteCopia, notas, meta());

        boolean hablaDeLoQueFalta = !unaSola && falta != null && antes != null && falta.nota > 0;
        // "en el parcial", o "en el parcial y el 10 % que falta de trabajos" si aún falta %
        String enQue = falta == null ? "" : TextoPendientes.describir(requireContext(), falta.enQue);
        String mensaje;
        if (unaSola) {
            // El parcial y la autoevaluación son una sola nota: "la autoevaluación queda en 4.5"
            mensaje = getString(R.string.nota_previa_una, Formato.nota(nota),
                    frase(componente), Formato.nota(despues));
        } else if (hablaDeLoQueFalta && despues > antes) {
            mensaje = getString(R.string.nota_previa_sube, Formato.nota(nota), frase(componente),
                    Formato.nota(despues), Formato.nota(falta.nota), enQue);
        } else if (hablaDeLoQueFalta && despues < antes) {
            mensaje = getString(R.string.nota_previa_baja, Formato.nota(nota), frase(componente),
                    Formato.nota(despues), Formato.nota(falta.nota), enQue);
        } else {
            mensaje = getString(R.string.nota_previa_simple, Formato.nota(nota), frase(componente),
                    Formato.nota(despues));
        }

        boolean mejora = antes == null || unaSola
                ? CalculadoraNotas.aprueba(despues, materia.notaParaPasar)
                : despues >= antes;
        textoVistaPrevia.setText(mensaje);
        textoVistaPrevia.setTextColor(ContextCompat.getColor(requireContext(),
                mejora ? R.color.aprobado : R.color.riesgo));
    }

    // ===== Guardar =====

    /**
     * Guarda la nota y vuelve. El aviso de abajo (pantalla 19) dice dónde quedó y deja
     * deshacer: borra la nota nueva o devuelve la que se reemplazó.
     */
    private void guardar() {
        Double valor = leerNota();
        if (valor == null) {
            return;
        }
        Repositorio datos = Repositorio.get();
        Componente componente = componenteElegido();
        Double peso = CalculadoraNotas.esUnaSolaNota(componente.id) ? null : leerPeso();
        if (peso != null && componente.promedioIgual) {
            datos.cambiarModo(materia.id, corte.numero, componente.id, false);
        }

        String escrito = texto(nombreNota).trim();
        Nota reemplazada = notaQueSeReemplaza();
        Nota nota = new Nota(corte.numero, componente.id, valor, peso);
        nota.fecha = LocalDate.now().toString();
        Runnable deshacer;
        String materiaId = materia.id;
        if (reemplazada != null) {
            Nota anterior = copiar(reemplazada);
            nota.id = reemplazada.id;
            nota.nombre = reemplazada.nombre;
            datos.editarNota(materiaId, nota);
            deshacer = () -> datos.editarNota(materiaId, anterior);
        } else {
            // El parcial y la autoevaluación no piden nombre: llevan el del tipo
            nota.nombre = escrito.isEmpty() || CalculadoraNotas.esUnaSolaNota(componente.id)
                    ? nombrePorDefecto(componente) : escrito;
            Nota guardada = datos.agregarNota(materiaId, nota);
            deshacer = () -> datos.borrarNota(materiaId, guardada.id);
        }

        String texto = getString(R.string.aviso_nota_guardada, materia.nombre,
                getString(R.string.forma_corte_numero, corte.numero));
        MainActivity actividad = (MainActivity) requireActivity();
        volver();
        actividad.avisar(texto, getString(R.string.aviso_deshacer), deshacer);
    }

    private String nombrePorDefecto(Componente componente) {
        if (CalculadoraNotas.esUnaSolaNota(componente.id)) {
            return componente.nombre;
        }
        int cuantas = 0;
        for (Nota nota : notasDeLaMateria()) {
            if (nota.corte == corte.numero && Objects.equals(nota.componenteId, componente.id)) {
                cuantas++;
            }
        }
        return getString(R.string.corte_nota_numero, cuantas + 1);
    }

    private static Nota copiar(Nota nota) {
        Nota copia = new Nota(nota.corte, nota.componenteId, nota.valor, nota.peso);
        copia.id = nota.id;
        copia.nombre = nota.nombre;
        copia.fecha = nota.fecha;
        return copia;
    }

    private void volver() {
        NavHostFragment.findNavController(this).popBackStack();
    }
}
