package com.dosnueve.app.ui.perfil;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dosnueve.app.R;
import com.dosnueve.app.avisos.Avisos;
import com.dosnueve.app.data.Cuenta;
import com.dosnueve.app.data.Repositorio;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 12 Perfil (doc 03, RF-05, RF-28 y RF-29). Exportar a PDF (RF-30) se quitó.
 *
 * <p>Los datos de la cuenta vienen de Firebase. Cerrar sesión devuelve a la pantalla 01.
 * Los interruptores de Avisos encienden las notificaciones (RF-31 y RF-32).
 */
public class PerfilFragment extends Fragment {

    private MaterialSwitch recordar;
    private MaterialSwitch riesgo;
    /** El interruptor que se encendió mientras el celular pregunta por el permiso. */
    @Nullable
    private CompoundButton esperandoPermiso;

    /** Desde Android 13 las notificaciones piden permiso; si lo niegan, el interruptor se apaga. */
    private final ActivityResultLauncher<String> pedirPermiso = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), concedido -> {
                CompoundButton cual = esperandoPermiso;
                esperandoPermiso = null;
                if (cual == null || !isAdded()) {
                    return;
                }
                if (concedido && Avisos.puedeNotificar(requireContext())) {
                    guardarAvisos();
                } else {
                    sinPermiso(cual);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup padre,
                             @Nullable Bundle estado) {
        return inflador.inflate(R.layout.fragment_perfil, padre, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        Repositorio datos = Repositorio.get();
        // Se repinta al escoger foto o avatar, y cuando el perfil llega de la nube
        datos.cambios().observe(getViewLifecycleOwner(), cambio ->
                mostrarCuenta(vista, datos.nombre(), datos.correo(), datos.universidad()));
        vista.findViewById(R.id.avatarPerfil).setOnClickListener(v ->
                new AvatarBottomSheet().show(getChildFragmentManager(), AvatarBottomSheet.ETIQUETA));

        // Recordatorio de los domingos (RF-31) y aviso de materia en rojo (RF-32)
        recordar = vista.findViewById(R.id.interruptorRecordar);
        riesgo = vista.findViewById(R.id.interruptorRiesgo);
        recordar.setChecked(datos.recordarNotas());
        riesgo.setChecked(datos.avisarRiesgo());
        CompoundButton.OnCheckedChangeListener alCambiar = (boton, encendido) -> {
            if (encendido && !Avisos.puedeNotificar(requireContext())) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Se guarda cuando conteste
                    esperandoPermiso = boton;
                    pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    // Las apagaron en Ajustes: desde aquí no se pueden prender
                    sinPermiso(boton);
                }
                return;
            }
            guardarAvisos();
        };
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

    private void guardarAvisos() {
        Repositorio.get().guardarAvisos(recordar.isChecked(), riesgo.isChecked());
    }

    /** Sin permiso no hay aviso: el interruptor vuelve a apagarse (y eso se guarda). */
    private void sinPermiso(CompoundButton cual) {
        cual.setChecked(false);
        Toast.makeText(requireContext(), R.string.perfil_sin_permiso, Toast.LENGTH_LONG).show();
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
        Avatar.pintar(vista.findViewById(R.id.foto), iniciales);
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
        Cuenta.cerrarSesion(requireContext());

        // Se borra todo el historial: después de cerrar sesión, atrás no puede
        // devolver a las pantallas de la cuenta anterior
        NavOptions sinHistorial = new NavOptions.Builder()
                .setPopUpTo(R.id.inicioFragment, true, false)
                .build();
        NavHostFragment.findNavController(this)
                .navigate(R.id.bienvenidaFragment, null, sinHistorial);
    }
}
