package com.dosnueve.app.ui.perfil;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.data.Repositorio;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 12 Perfil (doc 03, RF-05, RF-28 y RF-29). Exportar a PDF (RF-30) se quitó.
 *
 * <p>Los datos de la cuenta vienen de Google (RF-02). Cerrar sesión devuelve a la
 * pantalla 01 y borra el acceso de Firebase (fase 5).
 */
public class PerfilFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_perfil, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        Repositorio datos = Repositorio.get();
        mostrarCuenta(vista, datos.nombre(), datos.correo(), datos.universidad());

        // Los avisos todavía no se envían (fase 5), pero lo que se escoge queda guardado
        MaterialSwitch recordar = vista.findViewById(R.id.interruptorRecordar);
        MaterialSwitch riesgo = vista.findViewById(R.id.interruptorRiesgo);
        recordar.setChecked(datos.recordarNotas());
        riesgo.setChecked(datos.avisarRiesgo());
        CompoundButton.OnCheckedChangeListener alCambiar = (boton, encendido) ->
                datos.guardarAvisos(recordar.isChecked(), riesgo.isChecked());
        recordar.setOnCheckedChangeListener(alCambiar);
        riesgo.setOnCheckedChangeListener(alCambiar);

        armarOpcion(vista.findViewById(R.id.opcionFormaCalificar),
                R.drawable.ic_lapiz,
                R.string.perfil_forma_calificar,
                R.string.perfil_forma_calificar_detalle,
                R.id.formaCalificarFragment);

        armarOpcion(vista.findViewById(R.id.opcionFechas),
                R.drawable.ic_calendario,
                R.string.perfil_fechas,
                0,
                R.id.fechasFragment);

        vista.findViewById(R.id.botonCerrarSesion).setOnClickListener(v -> cerrarSesion());
    }

    private void mostrarCuenta(View vista, String nombre, String correo, String universidad) {
        TextView comoSeLlama = vista.findViewById(R.id.nombre);
        TextView suCorreo = vista.findViewById(R.id.correo);
        TextView suUniversidad = vista.findViewById(R.id.universidad);
        TextView iniciales = vista.findViewById(R.id.iniciales);

        comoSeLlama.setText(nombre.isEmpty() ? getString(R.string.perfil_sin_nombre) : nombre);
        suCorreo.setText(correo);
        suCorreo.setVisibility(correo.isEmpty() ? View.GONE : View.VISIBLE);
        suUniversidad.setText(universidad.isEmpty() ? getString(R.string.perfil_sin_universidad) : universidad);
        iniciales.setText(inicialesDe(nombre.isEmpty() ? correo : nombre));
    }

    /** Iniciales para el avatar mientras no carga la foto de Google. */
    private String inicialesDe(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return "";
        }
        String[] partes = nombre.trim().split("\\s+");
        StringBuilder iniciales = new StringBuilder();
        for (int i = 0; i < Math.min(2, partes.length); i++) {
            iniciales.append(Character.toUpperCase(partes[i].charAt(0)));
        }
        return iniciales.toString();
    }

    private void armarOpcion(View opcion, @DrawableRes int icono, @StringRes int titulo,
                             @StringRes int detalle, int destino) {
        ImageView suIcono = opcion.findViewById(R.id.icono);
        TextView suTitulo = opcion.findViewById(R.id.titulo);
        TextView suDetalle = opcion.findViewById(R.id.detalle);

        suIcono.setImageResource(icono);
        suTitulo.setText(titulo);

        if (detalle == 0) {
            suDetalle.setVisibility(View.GONE);
        } else {
            suDetalle.setVisibility(View.VISIBLE);
            suDetalle.setText(detalle);
        }

        opcion.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(destino));
    }

    private void cerrarSesion() {
        // Fase 5: FirebaseAuth.getInstance().signOut() y limpiar las credenciales.
        // Las materias y notas se quedan en el celular
        Repositorio.get().cerrarSesion();

        // Se borra todo el historial: después de cerrar sesión, atrás no puede
        // devolver a las pantallas de la cuenta anterior
        NavOptions sinHistorial = new NavOptions.Builder()
                .setPopUpTo(R.id.inicioFragment, true, false)
                .build();
        NavHostFragment.findNavController(this)
                .navigate(R.id.bienvenidaFragment, null, sinHistorial);
    }
}
