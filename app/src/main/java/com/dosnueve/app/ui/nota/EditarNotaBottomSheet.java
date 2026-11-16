package com.dosnueve.app.ui.nota;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 13 Editar nota · 14 Borrar nota (doc 03, RF-18), y la hoja de "+ Nota" dentro de un corte.
 *
 * <p>Guarda directo en el {@link Repositorio}; la pantalla de atrás se repinta sola.
 *
 * <p>Para una nota nueva ya se sabe la materia, el corte y el tipo, así que solo pide lo
 * que falta: el parcial y la autoevaluación son una sola nota; los demás tipos llevan
 * nombre y, si van "Con porcentajes", cuánto vale del corte (RN-04).
 *
 * <p>Al borrar se pide confirmación y se dice cómo cambia el promedio del componente
 * ("pasaría de 3.6 a 3.5"), calculado con CalculadoraNotas antes de borrar.
 */
public class EditarNotaBottomSheet extends BottomSheetDialogFragment {

    public static final String ETIQUETA = "editarNota";

    private static final String ARG_MATERIA = "materiaId";
    private static final String ARG_NOTA = "notaId";
    private static final String ARG_CORTE = "corte";
    private static final String ARG_COMPONENTE = "componenteId";

    private String materiaId;
    private Materia materia;
    private Corte corte;
    private Componente componente;
    /** Las notas de la materia sin la que se está editando. */
    private List<Nota> notasBase;
    /** {@code null} si es una nota nueva. */
    @Nullable
    private Nota original;
    /** Trabajos y quices (y los tipos de varias notas) dicen cuánto vale cada nota. */
    private boolean llevaPeso;
    /** Obligatorio cuando el componente ya va "Con porcentajes" (RN-04). */
    private boolean pidePeso;
    private LocalDate fecha = LocalDate.now();

    private TextView valorNota;
    private TextView valorPeso;
    private TextView nombre;
    private TextView campoFecha;
    private TextView pesoDisponible;
    private TextView efecto;
    private MaterialButton botonGuardar;

    /** Hoja de "+ Nota" para un componente de un corte. */
    public static EditarNotaBottomSheet nueva(String materiaId, int corte, String componenteId) {
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_MATERIA, materiaId);
        argumentos.putInt(ARG_CORTE, corte);
        argumentos.putString(ARG_COMPONENTE, componenteId);
        EditarNotaBottomSheet hoja = new EditarNotaBottomSheet();
        hoja.setArguments(argumentos);
        return hoja;
    }

    /** 13 Editar nota. */
    public static EditarNotaBottomSheet editar(String materiaId, String notaId) {
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_MATERIA, materiaId);
        argumentos.putString(ARG_NOTA, notaId);
        EditarNotaBottomSheet hoja = new EditarNotaBottomSheet();
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.hoja_editar_nota, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        if (!leerDatos()) {
            dismiss();
            return;
        }
        boolean esNueva = original == null;
        boolean variasNotas = !CalculadoraNotas.esUnaSolaNota(componente.id);
        llevaPeso = variasNotas;
        pidePeso = variasNotas && !componente.promedioIgual;

        botonGuardar = vista.findViewById(R.id.botonGuardar);
        valorNota = vista.findViewById(R.id.valorNota);
        valorPeso = vista.findViewById(R.id.valorPeso);
        nombre = vista.findViewById(R.id.nombre);
        campoFecha = vista.findViewById(R.id.fecha);
        pesoDisponible = vista.findViewById(R.id.pesoDisponible);
        efecto = vista.findViewById(R.id.efecto);

        TextView titulo = vista.findViewById(R.id.titulo);
        TextView ubicacion = vista.findViewById(R.id.ubicacion);
        if (esNueva) {
            titulo.setText(variasNotas ? R.string.hoja_titulo_trabajos
                    : CalculadoraNotas.ID_AUTOEVALUACION.equals(componente.id) ? R.string.hoja_titulo_auto
                    : R.string.hoja_titulo_parcial);
            botonGuardar.setText(R.string.nota_guardar);
        }
        ubicacion.setText(getString(R.string.hoja_ubicacion, materia.nombre,
                getString(R.string.forma_corte_numero, corte.numero), componente.nombre));

        vista.findViewById(R.id.grupoNombre).setVisibility(variasNotas ? View.VISIBLE : View.GONE);
        // Una nota nueva se guarda con la fecha de hoy
        vista.findViewById(R.id.grupoFecha).setVisibility(esNueva ? View.GONE : View.VISIBLE);
        // Un quiz que vale 15 no puede llevarse todo el 30 % de trabajos: se dice cuánto vale
        vista.findViewById(R.id.grupoPeso).setVisibility(llevaPeso ? View.VISIBLE : View.GONE);
        pesoDisponible.setVisibility(llevaPeso ? View.VISIBLE : View.GONE);
        ((TextView) vista.findViewById(R.id.etiquetaPeso))
                .setText(pidePeso ? R.string.nota_peso : R.string.nota_peso_opcional);
        View botonBorrar = vista.findViewById(R.id.botonBorrar);
        botonBorrar.setVisibility(esNueva ? View.GONE : View.VISIBLE);

        if (!esNueva) {
            nombre.setText(original.nombre);
            valorNota.setText(Formato.nota(original.valor));
            if (original.peso != null) {
                valorPeso.setText(Formato.porcentaje(original.peso));
            }
            if (original.fecha != null) {
                fecha = LocalDate.parse(original.fecha);
            }
            campoFecha.setText(Formato.fecha(fecha.toString()));
            campoFecha.setOnClickListener(v -> escogerFecha());
            // El ícono del calendario también abre el calendario, no solo el texto
            ((TextInputLayout) vista.findViewById(R.id.campoFecha))
                    .setEndIconOnClickListener(v -> escogerFecha());
            botonBorrar.setOnClickListener(v -> confirmarBorrado());
        }

        TextWatcher alCambiar = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int i, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence t, int i, int a, int c) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                revisar();
            }
        };
        LimiteNota.vigilar((EditText) valorNota);
        valorNota.addTextChangedListener(alCambiar);
        valorPeso.addTextChangedListener(alCambiar);
        botonGuardar.setOnClickListener(v -> guardar());
        revisar();
    }

    /** @return {@code false} si la materia, el corte o la nota ya no existen. */
    private boolean leerDatos() {
        Bundle argumentos = requireArguments();
        Repositorio datos = Repositorio.get();
        materiaId = argumentos.getString(ARG_MATERIA);
        materia = datos.materia(materiaId);
        if (materia == null) {
            return false;
        }
        int numero;
        String componenteId;
        String notaId = argumentos.getString(ARG_NOTA);
        if (notaId == null) {
            numero = argumentos.getInt(ARG_CORTE);
            componenteId = argumentos.getString(ARG_COMPONENTE);
        } else {
            original = datos.nota(materiaId, notaId);
            if (original == null) {
                return false;
            }
            numero = original.corte;
            componenteId = original.componenteId;
        }
        corte = Repositorio.corte(materia, numero);
        componente = Repositorio.componente(materia, numero, componenteId);

        notasBase = new ArrayList<>();
        for (Nota nota : datos.notas(materiaId)) {
            if (original == null || !nota.id.equals(original.id)) {
                notasBase.add(nota);
            }
        }
        return corte != null && componente != null;
    }

    /** La meta del corte es lo que la materia necesita en lo que falta. */
    private double meta() {
        Double necesitas = CalculadoraNotas.resumen(materia, notasBase).necesitas;
        return necesitas == null ? materia.notaParaPasar : necesitas;
    }

    /** Valida nota y peso, y dice en una línea cómo queda el corte con esa nota. */
    private void revisar() {
        Double nota = leerNumero(valorNota);
        Double peso = leerPeso();

        double disponible = CalculadoraNotas.pesoDisponible(corte.numero, componente, notasBase);
        boolean sePasa = peso != null && peso > disponible;
        if (llevaPeso) {
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

        boolean notaValida = nota != null && nota >= 0 && nota <= CalculadoraNotas.NOTA_MAXIMA;
        boolean lista = notaValida && !sePasa && (!pidePeso || peso != null);
        botonGuardar.setEnabled(lista);
        if (!lista) {
            efecto.setVisibility(View.GONE);
            return;
        }
        mostrarEfecto(nota, peso);
    }

    /** "Te basta un 2.7 en el parcial." o, si ya no falta nada, "El corte queda en 3.4." */
    private void mostrarEfecto(double nota, @Nullable Double peso) {
        List<Nota> conEsta = new ArrayList<>(notasBase);
        conEsta.add(new Nota(corte.numero, componente.id, nota, peso));
        double meta = meta();
        Corte comoQueda = corteConModo(peso);

        CalculadoraNotas.NecesarioEnCorte falta = CalculadoraNotas.necesarioEnCorte(comoQueda, conEsta, meta);
        String mensaje;
        boolean bien;
        if (falta == null) {
            Double notaCorte = CalculadoraNotas.notaCorte(comoQueda, conEsta);
            mensaje = getString(R.string.hoja_efecto_corte, Formato.nota(notaCorte));
            bien = notaCorte != null && CalculadoraNotas.aprueba(notaCorte, meta);
        } else if (falta.nota <= 0) {
            mensaje = getString(R.string.hoja_efecto_ya_llegas);
            bien = true;
        } else {
            // Sin suponer nada de lo que falta: "en el parcial y el 10 % que falta de trabajos"
            mensaje = getString(R.string.hoja_efecto_basta, Formato.nota(falta.nota),
                    TextoPendientes.describir(requireContext(), falta.enQue));
            bien = falta.nota <= CalculadoraNotas.NOTA_MAXIMA;
        }
        efecto.setText(mensaje);
        efecto.setTextColor(ContextCompat.getColor(requireContext(), bien ? R.color.aprobado : R.color.riesgo));
        efecto.setVisibility(View.VISIBLE);
    }

    /** El "Vale %" escrito; {@code null} si el tipo no lo lleva, está vacío o en 0. */
    @Nullable
    private Double leerPeso() {
        if (!llevaPeso) {
            return null;
        }
        Double peso = leerNumero(valorPeso);
        return peso != null && peso > 0 ? peso : null;
    }

    /**
     * El corte para calcular el efecto: si se escribió cuánto vale una nota de un
     * componente que todavía valía igual, se calcula como "Con porcentajes", que es como
     * va a quedar. Es una copia: la forma guardada no se toca hasta guardar.
     */
    private Corte corteConModo(@Nullable Double peso) {
        if (peso == null || !componente.promedioIgual) {
            return corte;
        }
        List<Corte> uno = new ArrayList<>();
        uno.add(corte);
        Corte copia = Repositorio.copiar(uno).get(0);
        for (Componente c : copia.componentes) {
            if (c.id.equals(componente.id)) {
                c.promedioIgual = false;
            }
        }
        return copia;
    }

    private void guardar() {
        Double valor = leerNumero(valorNota);
        if (valor == null) {
            return;
        }
        Double peso = leerPeso();
        String escrito = nombre.getText() == null ? "" : nombre.getText().toString().trim();
        Repositorio datos = Repositorio.get();
        if (peso != null && componente.promedioIgual) {
            // Con un "Vale %" escrito, el componente pasa a "Con porcentajes" (RN-04)
            datos.cambiarModo(materiaId, corte.numero, componente.id, false);
        }

        Nota nota = new Nota(corte.numero, componente.id, valor, peso);
        if (original == null) {
            nota.nombre = escrito.isEmpty() ? nombrePorDefecto() : escrito;
            nota.fecha = LocalDate.now().toString();
            Nota guardada = datos.agregarNota(materiaId, nota);
            MainActivity actividad = (MainActivity) requireActivity();
            dismiss();
            actividad.avisar(getString(R.string.aviso_nota_guardada, materia.nombre,
                            getString(R.string.forma_corte_numero, corte.numero)),
                    getString(R.string.aviso_deshacer),
                    () -> datos.borrarNota(materiaId, guardada.id));
            return;
        }
        nota.id = original.id;
        nota.nombre = escrito.isEmpty() ? original.nombre : escrito;
        nota.fecha = fecha.toString();
        datos.editarNota(materiaId, nota);
        dismiss();
    }

    /** "Nota 3" en los tipos de varias notas; el nombre del tipo en el parcial y la auto. */
    private String nombrePorDefecto() {
        if (CalculadoraNotas.esUnaSolaNota(componente.id)) {
            return componente.nombre;
        }
        int cuantas = 0;
        for (Nota nota : notasBase) {
            if (nota.corte == corte.numero && Objects.equals(nota.componenteId, componente.id)) {
                cuantas++;
            }
        }
        return getString(R.string.corte_nota_numero, cuantas + 1);
    }

    private void escogerFecha() {
        MaterialDatePicker<Long> calendario = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.nota_fecha)
                .setSelection(fecha.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
                .build();
        calendario.addOnPositiveButtonClickListener(milisegundos -> {
            fecha = Instant.ofEpochMilli(milisegundos).atZone(ZoneId.of("UTC")).toLocalDate();
            campoFecha.setText(Formato.fecha(fecha.toString()));
        });
        calendario.show(getChildFragmentManager(), "calendario");
    }

    @Nullable
    private static Double leerNumero(TextView campo) {
        CharSequence texto = campo.getText();
        if (texto == null || texto.toString().trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(texto.toString().trim().replace(',', '.'));
        } catch (NumberFormatException sinNumero) {
            return null;
        }
    }

    // ===== Borrar =====

    /** 14 Borrar nota: confirmación con el efecto real en el promedio del componente. */
    private void confirmarBorrado() {
        List<Nota> todas = new ArrayList<>(notasBase);
        todas.add(original);
        Double antes = CalculadoraNotas.notaComponente(corte.numero, componente, todas);
        Double despues = CalculadoraNotas.notaComponente(corte.numero, componente, notasBase);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.borrar_nota_titulo)
                .setMessage(getString(R.string.borrar_nota_efecto,
                        componente.nombre, Formato.nota(antes), Formato.nota(despues)))
                .setNegativeButton(R.string.general_cancelar, null)
                .setPositiveButton(R.string.general_borrar, (dialogo, boton) -> borrar())
                .show();
    }

    private void borrar() {
        Repositorio datos = Repositorio.get();
        Nota borrada = datos.borrarNota(materiaId, original.id);
        MainActivity actividad = (MainActivity) requireActivity();
        dismiss();
        if (borrada != null) {
            actividad.avisar(getString(R.string.aviso_nota_borrada), getString(R.string.aviso_deshacer),
                    () -> datos.restaurarNota(materiaId, borrada));
        }
    }
}
